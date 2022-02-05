package com.android.internal.telephony;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.service.carrier.ICarrierMessagingCallback;
import android.service.carrier.ICarrierMessagingService;
import android.telephony.CarrierMessagingServiceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.EventLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class SMSDispatcher extends Handler {
    static final boolean DBG = false;
    private static final int EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE = 8;
    private static final int EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE = 9;
    protected static final int EVENT_HANDLE_STATUS_REPORT = 10;
    protected static final int EVENT_ICC_CHANGED = 15;
    protected static final int EVENT_IMS_STATE_CHANGED = 12;
    protected static final int EVENT_IMS_STATE_DONE = 13;
    protected static final int EVENT_NEW_ICC_SMS = 14;
    protected static final int EVENT_RADIO_ON = 11;
    static final int EVENT_SEND_CONFIRMED_SMS = 5;
    private static final int EVENT_SEND_LIMIT_REACHED_CONFIRMATION = 4;
    private static final int EVENT_SEND_RETRY = 3;
    protected static final int EVENT_SEND_SMS_COMPLETE = 2;
    protected static final int EVENT_SMS_ON_ICC = 16;
    static final int EVENT_STOP_SENDING = 7;
    private static final int MAX_SEND_RETRIES = 3;
    private static final int MO_MSG_QUEUE_LIMIT = 5;
    private static final int PREMIUM_RULE_USE_BOTH = 3;
    private static final int PREMIUM_RULE_USE_NETWORK = 2;
    private static final int PREMIUM_RULE_USE_SIM = 1;
    private static final String SEND_NEXT_MSG_EXTRA = "SendNextMsg";
    private static final String SEND_RESPOND_VIA_MESSAGE_PERMISSION = "android.permission.SEND_RESPOND_VIA_MESSAGE";
    private static final int SEND_RETRY_DELAY = 2000;
    private static final int SINGLE_PART_SMS = 1;
    static final String TAG = "SMSDispatcher";
    private static final String TEST_SIM = "00101";
    private static int sConcatenatedRef = new Random().nextInt(256);
    protected final CommandsInterface mCi;
    private boolean mConfirmation;
    protected final Context mContext;
    protected ImsSMSDispatcher mImsSMSDispatcher;
    private PackageInfo mOriginalAppInfo;
    private int mPendingTrackerCount;
    protected PhoneBase mPhone;
    protected final ContentResolver mResolver;
    protected boolean mSendConfirmed;
    private final SettingsObserver mSettingsObserver;
    protected boolean mSmsCapable;
    protected boolean mSmsSendDisabled;
    protected final TelephonyManager mTelephonyManager;
    private SmsUsageMonitor mUsageMonitor;
    private final AtomicInteger mPremiumSmsRule = new AtomicInteger(1);
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference<>();
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList<>();

    protected abstract GsmAlphabet.TextEncodingDetails calculateLength(CharSequence charSequence, boolean z);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract String getFormat();

    protected abstract SmsTracker getNewSubmitPduTracker(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract void sendSms(SmsTracker smsTracker);

    protected abstract void sendSmsByPstn(SmsTracker smsTracker);

    protected abstract void sendSubmitPdu(SmsTracker smsTracker);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, int i, boolean z, int i2);

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef++;
        return sConcatenatedRef;
    }

    public SMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        this.mSmsCapable = true;
        this.mPhone = phone;
        this.mImsSMSDispatcher = imsSMSDispatcher;
        this.mContext = phone.getContext();
        this.mResolver = this.mContext.getContentResolver();
        this.mCi = phone.mCi;
        this.mUsageMonitor = usageMonitor;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mSettingsObserver = new SettingsObserver(this, this.mPremiumSmsRule, this.mContext);
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("sms_short_code_rule"), false, this.mSettingsObserver);
        this.mSmsCapable = this.mContext.getResources().getBoolean(17956949);
        this.mSmsSendDisabled = !SystemProperties.getBoolean("telephony.sms.send", this.mSmsCapable);
        Rlog.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + this.mSmsCapable + " format=" + getFormat() + " mSmsSendDisabled=" + this.mSmsSendDisabled);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicInteger mPremiumSmsRule;

        SettingsObserver(Handler handler, AtomicInteger premiumSmsRule, Context context) {
            super(handler);
            this.mPremiumSmsRule = premiumSmsRule;
            this.mContext = context;
            onChange(false);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            this.mPremiumSmsRule.set(Settings.Global.getInt(this.mContext.getContentResolver(), "sms_short_code_rule", 1));
        }
    }

    public void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mUsageMonitor = phone.mSmsUsageMonitor;
        Rlog.d(TAG, "Active phone changed to " + this.mPhone.getPhoneName());
    }

    public void dispose() {
        this.mContext.getContentResolver().unregisterContentObserver(this.mSettingsObserver);
    }

    protected void handleStatusReport(Object o) {
        Rlog.d(TAG, "handleStatusReport() called with no subclass.");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 2:
                handleSendComplete((AsyncResult) msg.obj);
                return;
            case 3:
                Rlog.d(TAG, "SMS retry..");
                sendRetrySms((SmsTracker) msg.obj);
                return;
            case 4:
                handleReachSentLimit((SmsTracker) msg.obj);
                return;
            case 5:
                SmsTracker tracker = (SmsTracker) msg.obj;
                if (tracker.isMultipart()) {
                    tracker.mSendConfirmed = true;
                    sendMultipartSms(tracker);
                } else {
                    if (this.mPendingTrackerCount > 1) {
                        tracker.mExpectMore = true;
                    } else {
                        tracker.mExpectMore = false;
                    }
                    sendSms(tracker);
                }
                this.mPendingTrackerCount--;
                return;
            case 6:
            default:
                Rlog.e(TAG, "handleMessage() ignoring message of unexpected type " + msg.what);
                return;
            case 7:
                SmsTracker tracker2 = (SmsTracker) msg.obj;
                if (tracker2.isMultipart()) {
                    tracker2.onFailedMulti(this.mContext, 5, 0);
                } else {
                    tracker2.onFailed(this.mContext, 5, 0);
                }
                this.mPendingTrackerCount--;
                return;
            case 8:
                handleConfirmShortCode(false, (SmsTracker) msg.obj);
                return;
            case 9:
                handleConfirmShortCode(true, (SmsTracker) msg.obj);
                return;
            case 10:
                handleStatusReport(msg.obj);
                return;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public abstract class SmsSender extends CarrierMessagingServiceManager {
        protected volatile SmsSenderCallback mSenderCallback;
        protected final SmsTracker mTracker;

        protected SmsSender(SmsTracker tracker) {
            SMSDispatcher.this = r1;
            this.mTracker = tracker;
        }

        public void sendSmsByCarrierApp(String carrierPackageName, SmsSenderCallback senderCallback) {
            this.mSenderCallback = senderCallback;
            if (!bindToCarrierMessagingService(SMSDispatcher.this.mContext, carrierPackageName)) {
                Rlog.e(SMSDispatcher.TAG, "bindService() for carrier messaging service failed");
                this.mSenderCallback.onSendSmsComplete(1, 0);
                return;
            }
            Rlog.d(SMSDispatcher.TAG, "bindService() for carrier messaging service succeeded");
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    protected final class TextSmsSender extends SmsSender {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public TextSmsSender(SmsTracker tracker) {
            super(tracker);
            SMSDispatcher.this = r1;
        }

        @Override // android.telephony.CarrierMessagingServiceManager
        protected void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            String text = (String) this.mTracker.mData.get(Telephony.Mms.Part.TEXT);
            if (text != null) {
                try {
                    carrierMessagingService.sendTextSms(text, SMSDispatcher.this.getSubId(), this.mTracker.mDestAddress, this.mSenderCallback);
                } catch (RemoteException e) {
                    Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                    this.mSenderCallback.onSendSmsComplete(1, 0);
                }
            } else {
                this.mSenderCallback.onSendSmsComplete(1, 0);
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    protected final class DataSmsSender extends SmsSender {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public DataSmsSender(SmsTracker tracker) {
            super(tracker);
            SMSDispatcher.this = r1;
        }

        @Override // android.telephony.CarrierMessagingServiceManager
        protected void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            HashMap<String, Object> map = this.mTracker.mData;
            byte[] data = (byte[]) map.get("data");
            int destPort = ((Integer) map.get("destPort")).intValue();
            if (data != null) {
                try {
                    carrierMessagingService.sendDataSms(data, SMSDispatcher.this.getSubId(), this.mTracker.mDestAddress, destPort, this.mSenderCallback);
                } catch (RemoteException e) {
                    Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                    this.mSenderCallback.onSendSmsComplete(1, 0);
                }
            } else {
                this.mSenderCallback.onSendSmsComplete(1, 0);
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    protected final class SmsSenderCallback extends ICarrierMessagingCallback.Stub {
        private final SmsSender mSmsSender;

        public SmsSenderCallback(SmsSender smsSender) {
            SMSDispatcher.this = r1;
            this.mSmsSender = smsSender;
        }

        public void onSendSmsComplete(int result, int messageRef) {
            this.mSmsSender.disposeConnection(SMSDispatcher.this.mContext);
            SMSDispatcher.this.processSendSmsResponse(this.mSmsSender.mTracker, result, messageRef);
        }

        public void onSendMultipartSmsComplete(int result, int[] messageRefs) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMultipartSmsComplete call with result: " + result);
        }

        public void onFilterComplete(boolean keepMessage) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onFilterComplete call with result: " + keepMessage);
        }

        public void onSendMmsComplete(int result, byte[] sendConfPdu) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMmsComplete call with result: " + result);
        }

        public void onDownloadMmsComplete(int result) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onDownloadMmsComplete call with result: " + result);
        }
    }

    public void processSendSmsResponse(SmsTracker tracker, int result, int messageRef) {
        if (tracker == null) {
            Rlog.e(TAG, "processSendSmsResponse: null tracker");
            return;
        }
        SmsResponse smsResponse = new SmsResponse(messageRef, null, -1);
        switch (result) {
            case 0:
                Rlog.d(TAG, "Sending SMS by IP succeeded.");
                sendMessage(obtainMessage(2, new AsyncResult(tracker, smsResponse, (Throwable) null)));
                return;
            case 1:
                Rlog.d(TAG, "Sending SMS by IP failed. Retry on carrier network.");
                sendSubmitPdu(tracker);
                return;
            case 2:
                Rlog.d(TAG, "Sending SMS by IP failed.");
                sendMessage(obtainMessage(2, new AsyncResult(tracker, smsResponse, new CommandException(CommandException.Error.GENERIC_FAILURE))));
                return;
            default:
                Rlog.d(TAG, "Unknown result " + result + " Retry on carrier network.");
                sendSubmitPdu(tracker);
                return;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class MultipartSmsSender extends CarrierMessagingServiceManager {
        private final List<String> mParts;
        private volatile MultipartSmsSenderCallback mSenderCallback;
        public final SmsTracker[] mTrackers;

        MultipartSmsSender(ArrayList<String> parts, SmsTracker[] trackers) {
            SMSDispatcher.this = r1;
            this.mParts = parts;
            this.mTrackers = trackers;
        }

        void sendSmsByCarrierApp(String carrierPackageName, MultipartSmsSenderCallback senderCallback) {
            this.mSenderCallback = senderCallback;
            if (!bindToCarrierMessagingService(SMSDispatcher.this.mContext, carrierPackageName)) {
                Rlog.e(SMSDispatcher.TAG, "bindService() for carrier messaging service failed");
                this.mSenderCallback.onSendMultipartSmsComplete(1, null);
                return;
            }
            Rlog.d(SMSDispatcher.TAG, "bindService() for carrier messaging service succeeded");
        }

        @Override // android.telephony.CarrierMessagingServiceManager
        protected void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            try {
                carrierMessagingService.sendMultipartTextSms(this.mParts, SMSDispatcher.this.getSubId(), this.mTrackers[0].mDestAddress, this.mSenderCallback);
            } catch (RemoteException e) {
                Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                this.mSenderCallback.onSendMultipartSmsComplete(1, null);
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class MultipartSmsSenderCallback extends ICarrierMessagingCallback.Stub {
        private final MultipartSmsSender mSmsSender;

        MultipartSmsSenderCallback(MultipartSmsSender smsSender) {
            SMSDispatcher.this = r1;
            this.mSmsSender = smsSender;
        }

        public void onSendSmsComplete(int result, int messageRef) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendSmsComplete call with result: " + result);
        }

        public void onSendMultipartSmsComplete(int result, int[] messageRefs) {
            this.mSmsSender.disposeConnection(SMSDispatcher.this.mContext);
            if (this.mSmsSender.mTrackers == null) {
                Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMultipartSmsComplete call with null trackers.");
                return;
            }
            for (int i = 0; i < this.mSmsSender.mTrackers.length; i++) {
                int messageRef = 0;
                if (messageRefs != null && messageRefs.length > i) {
                    messageRef = messageRefs[i];
                }
                SMSDispatcher.this.processSendSmsResponse(this.mSmsSender.mTrackers[i], result, messageRef);
            }
        }

        public void onFilterComplete(boolean keepMessage) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onFilterComplete call with result: " + keepMessage);
        }

        public void onSendMmsComplete(int result, byte[] sendConfPdu) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMmsComplete call with result: " + result);
        }

        public void onDownloadMmsComplete(int result) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onDownloadMmsComplete call with result: " + result);
        }
    }

    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = (SmsTracker) ar.userObj;
        PendingIntent pendingIntent = tracker.mSentIntent;
        if (ar.result != null) {
            tracker.mMessageRef = ((SmsResponse) ar.result).mMessageRef;
        } else {
            Rlog.d(TAG, "SmsResponse was null");
        }
        if (ar.exception == null) {
            if (tracker.mDeliveryIntent != null) {
                this.deliveryPendingList.add(tracker);
            }
            tracker.onSent(this.mContext);
            return;
        }
        int ss = this.mPhone.getServiceState().getState();
        int ssVoice = this.mPhone.getServiceState().getVoiceRegState();
        int ssData = this.mPhone.getServiceState().getDataRegState();
        if (tracker.mImsRetry > 0 && ssVoice != 0 && ssData != 0) {
            tracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SSVoice= " + this.mPhone.getServiceState().getVoiceRegState() + " SSData= " + this.mPhone.getServiceState().getDataRegState() + " SS= " + this.mPhone.getServiceState().getState());
        } else if (isTestSim()) {
            tracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  because SIM be TestSIM");
        }
        if (!isIms() && ssVoice != 0 && ssData != 0) {
            tracker.onFailed(this.mContext, getNotInServiceError(ss), 0);
        } else if (((CommandException) ar.exception).getCommandError() != CommandException.Error.SMS_FAIL_RETRY || tracker.mRetryCount >= 3) {
            int errorCode = 0;
            if (ar.result != null) {
                errorCode = ((SmsResponse) ar.result).mErrorCode;
            }
            int error = 1;
            if (((CommandException) ar.exception).getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
                error = 6;
            }
            tracker.onFailed(this.mContext, error, errorCode);
        } else {
            tracker.mRetryCount++;
            sendMessageDelayed(obtainMessage(3, tracker), 2000L);
        }
    }

    protected static void handleNotInService(int ss, PendingIntent sentIntent) {
        if (sentIntent != null) {
            try {
                if (ss == 3) {
                    sentIntent.send(2);
                } else {
                    sentIntent.send(4);
                }
            } catch (PendingIntent.CanceledException e) {
            }
        }
    }

    protected static int getNotInServiceError(int ss) {
        return ss == 3 ? 2 : 4;
    }

    public void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg, int priority, boolean isExpectMore, int validityPeriod) {
        String fullMessageText = getMultipartMessageText(parts);
        int refNumber = getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        GsmAlphabet.TextEncodingDetails[] encodingForParts = new GsmAlphabet.TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            GsmAlphabet.TextEncodingDetails details = calculateLength(parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }
        SmsTracker[] trackers = new SmsTracker[msgCount];
        AtomicInteger unsentPartCount = new AtomicInteger(msgCount);
        AtomicBoolean anyPartFailed = new AtomicBoolean(false);
        int i2 = 0;
        while (i2 < msgCount) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i2 + 1;
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == 1) {
                smsHeader.languageTable = encodingForParts[i2].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i2].languageShiftTable;
            }
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i2) {
                sentIntent = sentIntents.get(i2);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i2) {
                deliveryIntent = deliveryIntents.get(i2);
            }
            if (getFormat() == SmsMessage.FORMAT_3GPP) {
                if (i2 == 0) {
                    trackers[i2] = getNewSubmitPduTrackerForFirstPart(destAddr, scAddr, parts.get(i2), smsHeader, encoding, sentIntent, deliveryIntent, i2 == msgCount + (-1), priority, isExpectMore, validityPeriod, unsentPartCount, anyPartFailed, messageUri, fullMessageText, getSmsTracker(getSmsTrackerMap(destAddr, scAddr, parts, sentIntents, deliveryIntents), sentIntent, deliveryIntent, getFormat(), messageUri, isExpectMore, fullMessageText, true));
                } else {
                    trackers[i2] = getNewSubmitPduTracker(destAddr, scAddr, parts.get(i2), smsHeader, encoding, sentIntent, deliveryIntent, i2 == msgCount + (-1), priority, isExpectMore, validityPeriod, unsentPartCount, anyPartFailed, messageUri, fullMessageText);
                }
                if (this.mConfirmation) {
                    this.mConfirmation = false;
                    return;
                }
            } else {
                trackers[i2] = getNewSubmitPduTracker(destAddr, scAddr, parts.get(i2), smsHeader, encoding, sentIntent, deliveryIntent, i2 == msgCount + (-1), priority, isExpectMore, validityPeriod, unsentPartCount, anyPartFailed, messageUri, fullMessageText);
            }
            i2++;
        }
        this.mOriginalAppInfo = null;
        if (parts == null || trackers == null || trackers.length == 0 || trackers[0] == null) {
            Rlog.e(TAG, "Cannot send multipart text. parts=" + parts + " trackers=" + trackers);
            return;
        }
        String carrierPackage = getCarrierAppPackageName();
        if (carrierPackage != null) {
            Rlog.d(TAG, "Found carrier package.");
            MultipartSmsSender smsSender = new MultipartSmsSender(parts, trackers);
            smsSender.sendSmsByCarrierApp(carrierPackage, new MultipartSmsSenderCallback(smsSender));
            return;
        }
        Rlog.v(TAG, "No carrier package.");
        for (SmsTracker tracker : trackers) {
            if (tracker != null) {
                sendSubmitPdu(tracker);
            } else {
                Rlog.e(TAG, "Null tracker.");
            }
        }
    }

    private SmsTracker getNewSubmitPduTrackerForFirstPart(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, String fullMessageText, SmsTracker innerTracker) {
        SmsMessage.SubmitPdu pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable);
        if (pdu != null) {
            SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, pdu), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, !lastPart || isExpectMore, fullMessageText, true, validityPeriod);
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            tracker.mFirstOfMultiPart = !lastPart && concatRef.seqNumber == 1;
            tracker.mMsgCount = concatRef.msgCount;
            tracker.mSmsTracker = innerTracker;
            tracker.mSendConfirmed = this.mSendConfirmed;
            return tracker;
        }
        Rlog.e(TAG, "SMSDispatcher.sendNewSubmitPduForFirstPart(): getSubmitPdu() returned null");
        return null;
    }

    protected void sendRawPdu(SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.mData.get("pdu");
        if (this.mSmsSendDisabled) {
            Rlog.e(TAG, "Device does not support sending sms.");
            tracker.onFailed(this.mContext, 4, 0);
        } else if (pdu == null) {
            Rlog.e(TAG, "Empty PDU");
            tracker.onFailed(this.mContext, 3, 0);
        } else {
            PackageManager pm = this.mContext.getPackageManager();
            String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
            if (packageNames == null || packageNames.length == 0) {
                Rlog.e(TAG, "Can't get calling app package name: refusing to send SMS");
                tracker.onFailed(this.mContext, 1, 0);
                return;
            }
            try {
                PackageInfo appInfo = pm.getPackageInfo(packageNames[0], 64);
                if (checkDestination(tracker)) {
                    if (getFormat() == android.telephony.SmsMessage.FORMAT_3GPP) {
                        if (tracker.mMsgCount == 0) {
                            if (!this.mUsageMonitor.check(appInfo.packageName, 1)) {
                                sendMessage(obtainMessage(4, tracker));
                                return;
                            }
                        } else if (tracker.mFirstOfMultiPart && !tracker.mSendConfirmed && !this.mUsageMonitor.check(appInfo.packageName, tracker.mMsgCount)) {
                            if (tracker.mSmsTracker == null) {
                                sendMessage(obtainMessage(4, tracker));
                            } else {
                                sendMessage(obtainMessage(4, tracker.mSmsTracker));
                            }
                            this.mConfirmation = true;
                            return;
                        }
                    } else if (!this.mUsageMonitor.check(appInfo.packageName, 1)) {
                        sendMessage(obtainMessage(4, tracker));
                        return;
                    }
                    if (this.mOriginalAppInfo != null) {
                        tracker.mOriginalAppInfo = this.mOriginalAppInfo;
                    }
                    sendSms(tracker);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Rlog.e(TAG, "Can't get calling app package info: refusing to send SMS");
                tracker.onFailed(this.mContext, 1, 0);
            }
        }
    }

    boolean checkDestination(SmsTracker tracker) {
        int event;
        if (this.mContext.checkCallingOrSelfPermission(SEND_RESPOND_VIA_MESSAGE_PERMISSION) == 0) {
            return true;
        }
        int rule = this.mPremiumSmsRule.get();
        int smsCategory = 0;
        if (rule == 1 || rule == 3) {
            String simCountryIso = this.mTelephonyManager.getSimCountryIso();
            if (simCountryIso == null || simCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get SIM country Iso: trying network country Iso");
                simCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            }
            smsCategory = this.mUsageMonitor.checkDestination(tracker.mDestAddress, simCountryIso);
        }
        if (rule == 2 || rule == 3) {
            String networkCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            if (networkCountryIso == null || networkCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get Network country Iso: trying SIM country Iso");
                networkCountryIso = this.mTelephonyManager.getSimCountryIso();
            }
            smsCategory = SmsUsageMonitor.mergeShortCodeCategories(smsCategory, this.mUsageMonitor.checkDestination(tracker.mDestAddress, networkCountryIso));
        }
        if (smsCategory == 0 || smsCategory == 1 || smsCategory == 2) {
            return true;
        }
        int premiumSmsPermission = this.mUsageMonitor.getPremiumSmsPermission(tracker.mAppInfo.packageName);
        if (premiumSmsPermission == 0) {
            premiumSmsPermission = 1;
        }
        switch (premiumSmsPermission) {
            case 2:
                Rlog.w(TAG, "User denied this app from sending to premium SMS");
                sendMessage(obtainMessage(7, tracker));
                return false;
            case 3:
                Rlog.d(TAG, "User approved this app to send to premium SMS");
                return true;
            default:
                if (smsCategory == 3) {
                    event = 8;
                } else {
                    event = 9;
                }
                if (getFormat() != android.telephony.SmsMessage.FORMAT_3GPP) {
                    sendMessage(obtainMessage(event, tracker));
                } else if (tracker.mMsgCount == 0) {
                    sendMessage(obtainMessage(event, tracker));
                } else if (tracker.mFirstOfMultiPart && !tracker.mSendConfirmed) {
                    if (tracker.mSmsTracker == null) {
                        Rlog.d(TAG, "Comfirm send to premium short code");
                        sendMessage(obtainMessage(event, tracker));
                    } else {
                        Rlog.d(TAG, "Comfirm send to premium multipart short code");
                        sendMessage(obtainMessage(event, tracker.mSmsTracker));
                    }
                    this.mConfirmation = true;
                } else if (tracker.mSendConfirmed) {
                    Rlog.d(TAG, "already confirmed.");
                    return true;
                }
                return false;
        }
    }

    private boolean denyIfQueueLimitReached(SmsTracker tracker) {
        if (this.mPendingTrackerCount >= 5) {
            Rlog.e(TAG, "Denied because queue limit reached");
            tracker.onFailed(this.mContext, 5, 0);
            return true;
        }
        this.mPendingTrackerCount++;
        return false;
    }

    private CharSequence getAppLabel(String appPackage) {
        PackageManager pm = this.mContext.getPackageManager();
        try {
            return pm.getApplicationInfo(appPackage, 0).loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Rlog.e(TAG, "PackageManager Name Not Found for package " + appPackage);
            return appPackage;
        }
    }

    protected void handleReachSentLimit(SmsTracker tracker) {
        if (!denyIfQueueLimitReached(tracker)) {
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(17040679, appLabel));
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, null);
            AlertDialog d = new AlertDialog.Builder(this.mContext).setTitle(17040678).setIcon(17301642).setMessage(messageText).setPositiveButton(r.getString(17040680), listener).setNegativeButton(r.getString(17040681), listener).setOnCancelListener(listener).create();
            d.getWindow().setType(2003);
            d.show();
        }
    }

    protected void handleConfirmShortCode(boolean isPremium, SmsTracker tracker) {
        int detailsId;
        if (!denyIfQueueLimitReached(tracker)) {
            if (isPremium) {
                detailsId = 17040684;
            } else {
                detailsId = 17040683;
            }
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(17040682, appLabel, tracker.mDestAddress));
            View layout = ((LayoutInflater) this.mContext.getSystemService("layout_inflater")).inflate(17367255, (ViewGroup) null);
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, (TextView) layout.findViewById(16909222));
            ((TextView) layout.findViewById(16909216)).setText(messageText);
            ((TextView) ((ViewGroup) layout.findViewById(16909217)).findViewById(16909219)).setText(detailsId);
            ((CheckBox) layout.findViewById(16909220)).setOnCheckedChangeListener(listener);
            AlertDialog d = new AlertDialog.Builder(this.mContext).setView(layout).setPositiveButton(r.getString(17040685), listener).setNegativeButton(r.getString(17040686), listener).setOnCancelListener(listener).create();
            d.getWindow().setType(2003);
            d.show();
            listener.setPositiveButton(d.getButton(-1));
            listener.setNegativeButton(d.getButton(-2));
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mUsageMonitor.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mUsageMonitor.setPremiumSmsPermission(packageName, permission);
    }

    public void sendRetrySms(SmsTracker tracker) {
        if (this.mImsSMSDispatcher != null) {
            this.mImsSMSDispatcher.sendRetrySms(tracker);
        } else {
            Rlog.e(TAG, this.mImsSMSDispatcher + " is null. Retry failed");
        }
    }

    private void sendMultipartSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        String destinationAddress = (String) map.get("destAddr");
        String scAddress = (String) map.get("scaddress");
        ArrayList<String> parts = (ArrayList) map.get("parts");
        ArrayList<PendingIntent> sentIntents = (ArrayList) map.get("sentIntents");
        ArrayList<PendingIntent> deliveryIntents = (ArrayList) map.get("deliveryIntents");
        int ss = this.mPhone.getServiceState().getState();
        int ssVoice = this.mPhone.getServiceState().getVoiceRegState();
        int ssData = this.mPhone.getServiceState().getDataRegState();
        Rlog.d(TAG, "isIms = " + isIms() + ", ssVoice = " + ssVoice + ", ssData = " + ssData);
        if (isIms() || ssVoice == 0 || ssData == 0) {
            this.mSendConfirmed = tracker.mSendConfirmed;
            this.mOriginalAppInfo = tracker.mAppInfo;
            sendMultipartText(destinationAddress, scAddress, parts, sentIntents, deliveryIntents, null, null, -1, tracker.mExpectMore, tracker.mvalidityPeriod);
            this.mSendConfirmed = false;
            return;
        }
        int count = parts.size();
        for (int i = 0; i < count; i++) {
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }
            handleNotInService(ss, sentIntent);
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static final class SmsTracker {
        private AtomicBoolean mAnyPartFailed;
        public final PackageInfo mAppInfo;
        public final HashMap<String, Object> mData;
        public final PendingIntent mDeliveryIntent;
        public final String mDestAddress;
        public boolean mExpectMore;
        public boolean mFirstOfMultiPart;
        String mFormat;
        private String mFullMessageText;
        public int mImsRetry;
        private boolean mIsText;
        public int mMessageRef;
        public Uri mMessageUri;
        public int mMsgCount;
        public PackageInfo mOriginalAppInfo;
        private int mPhoneId;
        public int mRetryCount;
        public boolean mSendConfirmed;
        public final PendingIntent mSentIntent;
        public final SmsHeader mSmsHeader;
        public SmsTracker mSmsTracker;
        private long mTimestamp;
        private AtomicInteger mUnsentPartCount;
        public int mvalidityPeriod;

        private SmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, PackageInfo appInfo, String destAddr, String format, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, SmsHeader smsHeader, boolean isExpectMore, String fullMessageText, int phoneId, boolean isText, int validityPeriod) {
            this.mTimestamp = System.currentTimeMillis();
            this.mData = data;
            this.mSentIntent = sentIntent;
            this.mDeliveryIntent = deliveryIntent;
            this.mRetryCount = 0;
            this.mAppInfo = appInfo;
            this.mDestAddress = destAddr;
            this.mFormat = format;
            this.mExpectMore = isExpectMore;
            this.mImsRetry = 0;
            this.mMessageRef = 0;
            this.mvalidityPeriod = validityPeriod;
            this.mUnsentPartCount = unsentPartCount;
            this.mAnyPartFailed = anyPartFailed;
            this.mMessageUri = messageUri;
            this.mSmsHeader = smsHeader;
            this.mFullMessageText = fullMessageText;
            this.mPhoneId = phoneId;
            this.mIsText = isText;
        }

        boolean isMultipart() {
            return this.mData.containsKey("parts");
        }

        public void updateSentMessageStatus(Context context, int status) {
            if (this.mMessageUri != null) {
                ContentValues values = new ContentValues(1);
                values.put(Telephony.TextBasedSmsColumns.STATUS, Integer.valueOf(status));
                SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, values, (String) null, (String[]) null);
            }
        }

        private void updateMessageState(Context context, int messageType, int errorCode) {
            if (this.mMessageUri != null) {
                ContentValues values = new ContentValues(2);
                values.put("type", Integer.valueOf(messageType));
                values.put(Telephony.TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(errorCode));
                long identity = Binder.clearCallingIdentity();
                try {
                    if (SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, values, (String) null, (String[]) null) != 1) {
                        Rlog.e(SMSDispatcher.TAG, "Failed to move message to " + messageType);
                    }
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }

        private Uri persistSentMessageIfRequired(Context context, int messageType, int errorCode) {
            if (!this.mIsText) {
                return null;
            }
            if (this.mOriginalAppInfo == null) {
                if (!SmsApplication.shouldWriteMessageForPackage(this.mAppInfo.packageName, context)) {
                    return null;
                }
            } else if (!SmsApplication.shouldWriteMessageForPackage(this.mOriginalAppInfo.packageName, context)) {
                return null;
            }
            Rlog.d(SMSDispatcher.TAG, "Persist SMS into " + (messageType == 5 ? "FAILED" : "SENT"));
            ContentValues values = new ContentValues();
            values.put("phone_id", Integer.valueOf(this.mPhoneId));
            values.put("address", this.mDestAddress);
            values.put("body", this.mFullMessageText);
            values.put("date", Long.valueOf(System.currentTimeMillis()));
            values.put("seen", (Integer) 1);
            values.put("read", (Integer) 1);
            String creator = this.mAppInfo != null ? this.mAppInfo.packageName : null;
            if (!TextUtils.isEmpty(creator)) {
                values.put("creator", creator);
            }
            if (this.mDeliveryIntent != null) {
                values.put(Telephony.TextBasedSmsColumns.STATUS, (Integer) 32);
            }
            if (errorCode != 0) {
                values.put(Telephony.TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(errorCode));
            }
            long identity = Binder.clearCallingIdentity();
            ContentResolver resolver = context.getContentResolver();
            try {
                Uri uri = resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values);
                if (uri != null && messageType == 5) {
                    ContentValues updateValues = new ContentValues(1);
                    updateValues.put("type", (Integer) 5);
                    resolver.update(uri, updateValues, null, null);
                }
                return uri;
            } catch (Exception e) {
                Rlog.e(SMSDispatcher.TAG, "writeOutboxMessage: Failed to persist outbox message", e);
                return null;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        private void persistOrUpdateMessage(Context context, int messageType, int errorCode) {
            if (this.mMessageUri != null) {
                updateMessageState(context, messageType, errorCode);
            } else {
                this.mMessageUri = persistSentMessageIfRequired(context, messageType, errorCode);
            }
        }

        public void onFailed(Context context, int error, int errorCode) {
            if (this.mAnyPartFailed != null) {
                this.mAnyPartFailed.set(true);
            }
            boolean isSinglePartOrLastPart = true;
            if (this.mUnsentPartCount != null) {
                if (this.mUnsentPartCount.decrementAndGet() == 0) {
                    isSinglePartOrLastPart = true;
                } else {
                    isSinglePartOrLastPart = false;
                }
            }
            if (isSinglePartOrLastPart) {
                persistOrUpdateMessage(context, 5, errorCode);
            }
            if (this.mSentIntent != null) {
                try {
                    Intent fillIn = new Intent();
                    if (this.mMessageUri != null) {
                        fillIn.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (errorCode != 0) {
                        fillIn.putExtra("errorCode", errorCode);
                    }
                    if (this.mUnsentPartCount != null && isSinglePartOrLastPart) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                    }
                    this.mSentIntent.send(context, error, fillIn);
                } catch (PendingIntent.CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }

        /* JADX WARN: Code restructure failed: missing block: B:8:0x0012, code lost:
            persistOrUpdateMessage(r11, 5, r13);
         */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void onFailedMulti(android.content.Context r11, int r12, int r13) {
            /*
                r10 = this;
                r9 = 5
                r7 = 1
                r3 = 1
                java.util.concurrent.atomic.AtomicInteger r8 = r10.mUnsentPartCount
                if (r8 == 0) goto L_0x0010
                java.util.concurrent.atomic.AtomicInteger r8 = r10.mUnsentPartCount
                int r8 = r8.decrementAndGet()
                if (r8 != 0) goto L_0x0065
                r3 = r7
            L_0x0010:
                if (r3 == 0) goto L_0x0015
                r10.persistOrUpdateMessage(r11, r9, r13)
            L_0x0015:
                java.util.HashMap<java.lang.String, java.lang.Object> r4 = r10.mData
                java.lang.String r7 = "sentIntents"
                java.lang.Object r6 = r4.get(r7)
                java.util.ArrayList r6 = (java.util.ArrayList) r6
                java.util.Iterator r2 = r6.iterator()
            L_0x0023:
                boolean r7 = r2.hasNext()
                if (r7 == 0) goto L_0x0067
                java.lang.Object r5 = r2.next()
                android.app.PendingIntent r5 = (android.app.PendingIntent) r5
                if (r5 == 0) goto L_0x0023
                android.content.Intent r1 = new android.content.Intent     // Catch: CanceledException -> 0x005c
                r1.<init>()     // Catch: CanceledException -> 0x005c
                android.net.Uri r7 = r10.mMessageUri     // Catch: CanceledException -> 0x005c
                if (r7 == 0) goto L_0x0045
                java.lang.String r7 = "uri"
                android.net.Uri r8 = r10.mMessageUri     // Catch: CanceledException -> 0x005c
                java.lang.String r8 = r8.toString()     // Catch: CanceledException -> 0x005c
                r1.putExtra(r7, r8)     // Catch: CanceledException -> 0x005c
            L_0x0045:
                if (r13 == 0) goto L_0x004c
                java.lang.String r7 = "errorCode"
                r1.putExtra(r7, r13)     // Catch: CanceledException -> 0x005c
            L_0x004c:
                java.util.concurrent.atomic.AtomicInteger r7 = r10.mUnsentPartCount     // Catch: CanceledException -> 0x005c
                if (r7 == 0) goto L_0x0058
                if (r3 == 0) goto L_0x0058
                java.lang.String r7 = "SendNextMsg"
                r8 = 1
                r1.putExtra(r7, r8)     // Catch: CanceledException -> 0x005c
            L_0x0058:
                r5.send(r11, r12, r1)     // Catch: CanceledException -> 0x005c
                goto L_0x0023
            L_0x005c:
                r0 = move-exception
                java.lang.String r7 = "SMSDispatcher"
                java.lang.String r8 = "Failed to send result"
                android.telephony.Rlog.e(r7, r8)
                goto L_0x0023
            L_0x0065:
                r3 = 0
                goto L_0x0010
            L_0x0067:
                r10.persistOrUpdateMessage(r11, r9, r13)
                return
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SMSDispatcher.SmsTracker.onFailedMulti(android.content.Context, int, int):void");
        }

        public void onSent(Context context) {
            boolean isSinglePartOrLastPart = true;
            if (this.mUnsentPartCount != null) {
                isSinglePartOrLastPart = this.mUnsentPartCount.decrementAndGet() == 0;
            }
            if (isSinglePartOrLastPart) {
                int messageType = 2;
                if (this.mAnyPartFailed != null && this.mAnyPartFailed.get()) {
                    messageType = 5;
                }
                persistOrUpdateMessage(context, messageType, 0);
            }
            if (this.mSentIntent != null) {
                try {
                    Intent fillIn = new Intent();
                    if (this.mMessageUri != null) {
                        fillIn.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (this.mUnsentPartCount != null && isSinglePartOrLastPart) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                    }
                    this.mSentIntent.send(context, -1, fillIn);
                } catch (PendingIntent.CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, SmsHeader smsHeader, boolean isExpectMore, String fullMessageText, boolean isText, int validityPeriod) {
        PackageManager pm = this.mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
        PackageInfo appInfo = null;
        if (packageNames != null && packageNames.length > 0) {
            try {
                appInfo = pm.getPackageInfo(packageNames[0], 64);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return new SmsTracker(data, sentIntent, deliveryIntent, appInfo, PhoneNumberUtils.extractNetworkPortion((String) data.get("destAddr")), format, unsentPartCount, anyPartFailed, messageUri, smsHeader, isExpectMore, fullMessageText, this.mPhone.getPhoneId(), isText, validityPeriod);
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, Uri messageUri, boolean isExpectMore, String fullMessageText, boolean isText) {
        return getSmsTracker(data, sentIntent, deliveryIntent, format, null, null, messageUri, null, isExpectMore, fullMessageText, isText, -1);
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, Uri messageUri, boolean isExpectMore, String fullMessageText, boolean isText, int validityPeriod) {
        return getSmsTracker(data, sentIntent, deliveryIntent, format, null, null, messageUri, null, isExpectMore, fullMessageText, isText, validityPeriod);
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, String text, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put(Telephony.Mms.Part.TEXT, text);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, int destPort, int origPort, byte[] data, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("destPort", Integer.valueOf(destPort));
        map.put("origPort", Integer.valueOf(origPort));
        map.put("data", data);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    protected HashMap getSmsTrackerMap(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("destAddr", destAddr);
        map.put("scaddress", scAddr);
        map.put("parts", parts);
        map.put("sentIntents", sentIntents);
        map.put("deliveryIntents", deliveryIntents);
        return map;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class ConfirmDialogListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener, CompoundButton.OnCheckedChangeListener {
        private Button mNegativeButton;
        private Button mPositiveButton;
        private boolean mRememberChoice;
        private final TextView mRememberUndoInstruction;
        private final SmsTracker mTracker;

        ConfirmDialogListener(SmsTracker tracker, TextView textView) {
            SMSDispatcher.this = r1;
            this.mTracker = tracker;
            this.mRememberUndoInstruction = textView;
        }

        void setPositiveButton(Button button) {
            this.mPositiveButton = button;
        }

        void setNegativeButton(Button button) {
            this.mNegativeButton = button;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            int i = -1;
            int newSmsPermission = 1;
            if (which == -1) {
                Rlog.d(SMSDispatcher.TAG, "CONFIRM sending SMS");
                if (this.mTracker.mAppInfo.applicationInfo != null) {
                    i = this.mTracker.mAppInfo.applicationInfo.uid;
                }
                EventLog.writeEvent((int) EventLogTags.EXP_DET_SMS_SENT_BY_USER, i);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(5, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 3;
                }
            } else if (which == -2) {
                Rlog.d(SMSDispatcher.TAG, "DENY sending SMS");
                if (this.mTracker.mAppInfo.applicationInfo != null) {
                    i = this.mTracker.mAppInfo.applicationInfo.uid;
                }
                EventLog.writeEvent((int) EventLogTags.EXP_DET_SMS_DENIED_BY_USER, i);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 2;
                }
            }
            SMSDispatcher.this.setPremiumSmsPermission(this.mTracker.mAppInfo.packageName, newSmsPermission);
        }

        @Override // android.content.DialogInterface.OnCancelListener
        public void onCancel(DialogInterface dialog) {
            Rlog.d(SMSDispatcher.TAG, "dialog dismissed: don't send SMS");
            SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Rlog.d(SMSDispatcher.TAG, "remember this choice: " + isChecked);
            this.mRememberChoice = isChecked;
            if (isChecked) {
                this.mPositiveButton.setText(17040689);
                this.mNegativeButton.setText(17040690);
                if (this.mRememberUndoInstruction != null) {
                    this.mRememberUndoInstruction.setText(17040688);
                    this.mRememberUndoInstruction.setPadding(0, 0, 0, 32);
                    return;
                }
                return;
            }
            this.mPositiveButton.setText(17040685);
            this.mNegativeButton.setText(17040686);
            if (this.mRememberUndoInstruction != null) {
                this.mRememberUndoInstruction.setText("");
                this.mRememberUndoInstruction.setPadding(0, 0, 0, 0);
            }
        }
    }

    public boolean isIms() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.isIms();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public String getImsSmsFormat() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.getImsSmsFormat();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return null;
    }

    private String getMultipartMessageText(ArrayList<String> parts) {
        StringBuilder sb = new StringBuilder();
        Iterator i$ = parts.iterator();
        while (i$.hasNext()) {
            String part = i$.next();
            if (part != null) {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    protected String getCarrierAppPackageName() {
        List<String> carrierPackages;
        UiccCard card = UiccController.getInstance().getUiccCard(this.mPhone.getPhoneId());
        if (card == null || (carrierPackages = card.getCarrierPackageNamesForIntent(this.mContext.getPackageManager(), new Intent("android.service.carrier.CarrierMessagingService"))) == null || carrierPackages.size() != 1) {
            return null;
        }
        return carrierPackages.get(0);
    }

    protected int getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhone.mPhoneId);
    }

    private String getOperatorNumeric() {
        String operatorNumeric;
        String source;
        IccRecords r = this.mIccRecords.get();
        if (r == null) {
            operatorNumeric = null;
            source = "IccRecords == null";
        } else {
            operatorNumeric = r.getOperatorNumeric();
            source = "IccRecords";
        }
        Rlog.d(TAG, "getOperatorNumeric = " + operatorNumeric + " from " + source);
        return operatorNumeric;
    }

    private boolean isTestSim() {
        boolean ret = false;
        if (TEST_SIM.equals(getOperatorNumeric())) {
            ret = true;
        }
        Rlog.d(TAG, "isTestSim ret=" + ret);
        return ret;
    }
}
