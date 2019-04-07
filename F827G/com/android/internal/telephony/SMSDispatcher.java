package com.android.internal.telephony;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import android.provider.Settings.Global;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Sms.Sent;
import android.provider.Telephony.TextBasedSmsColumns;
import android.service.carrier.ICarrierMessagingCallback.Stub;
import android.service.carrier.ICarrierMessagingService;
import android.telephony.CarrierMessagingServiceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
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
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.gsm.SmsMessage.SubmitPdu;
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
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList();
    protected final CommandsInterface mCi;
    private boolean mConfirmation;
    protected final Context mContext;
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference();
    protected ImsSMSDispatcher mImsSMSDispatcher;
    private PackageInfo mOriginalAppInfo;
    private int mPendingTrackerCount;
    protected PhoneBase mPhone;
    private final AtomicInteger mPremiumSmsRule = new AtomicInteger(1);
    protected final ContentResolver mResolver;
    protected boolean mSendConfirmed;
    private final SettingsObserver mSettingsObserver;
    protected boolean mSmsCapable = true;
    protected boolean mSmsSendDisabled;
    protected final TelephonyManager mTelephonyManager;
    private SmsUsageMonitor mUsageMonitor;

    private final class ConfirmDialogListener implements OnClickListener, OnCancelListener, OnCheckedChangeListener {
        private Button mNegativeButton;
        private Button mPositiveButton;
        private boolean mRememberChoice;
        private final TextView mRememberUndoInstruction;
        private final SmsTracker mTracker;

        ConfirmDialogListener(SmsTracker smsTracker, TextView textView) {
            this.mTracker = smsTracker;
            this.mRememberUndoInstruction = textView;
        }

        public void onCancel(DialogInterface dialogInterface) {
            Rlog.d(SMSDispatcher.TAG, "dialog dismissed: don't send SMS");
            SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
        }

        public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            Rlog.d(SMSDispatcher.TAG, "remember this choice: " + z);
            this.mRememberChoice = z;
            if (z) {
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

        public void onClick(DialogInterface dialogInterface, int i) {
            int i2 = -1;
            if (i == -1) {
                Rlog.d(SMSDispatcher.TAG, "CONFIRM sending SMS");
                if (this.mTracker.mAppInfo.applicationInfo != null) {
                    i2 = this.mTracker.mAppInfo.applicationInfo.uid;
                }
                EventLog.writeEvent(EventLogTags.EXP_DET_SMS_SENT_BY_USER, i2);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(5, this.mTracker));
                if (this.mRememberChoice) {
                    i2 = 3;
                }
                i2 = 1;
            } else {
                if (i == -2) {
                    Rlog.d(SMSDispatcher.TAG, "DENY sending SMS");
                    if (this.mTracker.mAppInfo.applicationInfo != null) {
                        i2 = this.mTracker.mAppInfo.applicationInfo.uid;
                    }
                    EventLog.writeEvent(EventLogTags.EXP_DET_SMS_DENIED_BY_USER, i2);
                    SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
                    if (this.mRememberChoice) {
                        i2 = 2;
                    }
                }
                i2 = 1;
            }
            SMSDispatcher.this.setPremiumSmsPermission(this.mTracker.mAppInfo.packageName, i2);
        }

        /* Access modifiers changed, original: 0000 */
        public void setNegativeButton(Button button) {
            this.mNegativeButton = button;
        }

        /* Access modifiers changed, original: 0000 */
        public void setPositiveButton(Button button) {
            this.mPositiveButton = button;
        }
    }

    protected abstract class SmsSender extends CarrierMessagingServiceManager {
        protected volatile SmsSenderCallback mSenderCallback;
        protected final SmsTracker mTracker;

        protected SmsSender(SmsTracker smsTracker) {
            this.mTracker = smsTracker;
        }

        public void sendSmsByCarrierApp(String str, SmsSenderCallback smsSenderCallback) {
            this.mSenderCallback = smsSenderCallback;
            if (bindToCarrierMessagingService(SMSDispatcher.this.mContext, str)) {
                Rlog.d(SMSDispatcher.TAG, "bindService() for carrier messaging service succeeded");
                return;
            }
            Rlog.e(SMSDispatcher.TAG, "bindService() for carrier messaging service failed");
            this.mSenderCallback.onSendSmsComplete(1, 0);
        }
    }

    protected final class DataSmsSender extends SmsSender {
        public DataSmsSender(SmsTracker smsTracker) {
            super(smsTracker);
        }

        /* Access modifiers changed, original: protected */
        public void onServiceReady(ICarrierMessagingService iCarrierMessagingService) {
            HashMap hashMap = this.mTracker.mData;
            byte[] bArr = (byte[]) hashMap.get("data");
            int intValue = ((Integer) hashMap.get("destPort")).intValue();
            if (bArr != null) {
                try {
                    iCarrierMessagingService.sendDataSms(bArr, SMSDispatcher.this.getSubId(), this.mTracker.mDestAddress, intValue, this.mSenderCallback);
                    return;
                } catch (RemoteException e) {
                    Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                    this.mSenderCallback.onSendSmsComplete(1, 0);
                    return;
                }
            }
            this.mSenderCallback.onSendSmsComplete(1, 0);
        }
    }

    private final class MultipartSmsSender extends CarrierMessagingServiceManager {
        private final List<String> mParts;
        private volatile MultipartSmsSenderCallback mSenderCallback;
        public final SmsTracker[] mTrackers;

        MultipartSmsSender(ArrayList<String> arrayList, SmsTracker[] smsTrackerArr) {
            this.mParts = arrayList;
            this.mTrackers = smsTrackerArr;
        }

        /* Access modifiers changed, original: protected */
        public void onServiceReady(ICarrierMessagingService iCarrierMessagingService) {
            try {
                iCarrierMessagingService.sendMultipartTextSms(this.mParts, SMSDispatcher.this.getSubId(), this.mTrackers[0].mDestAddress, this.mSenderCallback);
            } catch (RemoteException e) {
                Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                this.mSenderCallback.onSendMultipartSmsComplete(1, null);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void sendSmsByCarrierApp(String str, MultipartSmsSenderCallback multipartSmsSenderCallback) {
            this.mSenderCallback = multipartSmsSenderCallback;
            if (bindToCarrierMessagingService(SMSDispatcher.this.mContext, str)) {
                Rlog.d(SMSDispatcher.TAG, "bindService() for carrier messaging service succeeded");
                return;
            }
            Rlog.e(SMSDispatcher.TAG, "bindService() for carrier messaging service failed");
            this.mSenderCallback.onSendMultipartSmsComplete(1, null);
        }
    }

    private final class MultipartSmsSenderCallback extends Stub {
        private final MultipartSmsSender mSmsSender;

        MultipartSmsSenderCallback(MultipartSmsSender multipartSmsSender) {
            this.mSmsSender = multipartSmsSender;
        }

        public void onDownloadMmsComplete(int i) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onDownloadMmsComplete call with result: " + i);
        }

        public void onFilterComplete(boolean z) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onFilterComplete call with result: " + z);
        }

        public void onSendMmsComplete(int i, byte[] bArr) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMmsComplete call with result: " + i);
        }

        public void onSendMultipartSmsComplete(int i, int[] iArr) {
            this.mSmsSender.disposeConnection(SMSDispatcher.this.mContext);
            if (this.mSmsSender.mTrackers == null) {
                Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMultipartSmsComplete call with null trackers.");
                return;
            }
            int i2 = 0;
            while (i2 < this.mSmsSender.mTrackers.length) {
                int i3 = (iArr == null || iArr.length <= i2) ? 0 : iArr[i2];
                SMSDispatcher.this.processSendSmsResponse(this.mSmsSender.mTrackers[i2], i, i3);
                i2++;
            }
        }

        public void onSendSmsComplete(int i, int i2) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendSmsComplete call with result: " + i);
        }
    }

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicInteger mPremiumSmsRule;

        SettingsObserver(Handler handler, AtomicInteger atomicInteger, Context context) {
            super(handler);
            this.mPremiumSmsRule = atomicInteger;
            this.mContext = context;
            onChange(false);
        }

        public void onChange(boolean z) {
            this.mPremiumSmsRule.set(Global.getInt(this.mContext.getContentResolver(), "sms_short_code_rule", 1));
        }
    }

    protected final class SmsSenderCallback extends Stub {
        private final SmsSender mSmsSender;

        public SmsSenderCallback(SmsSender smsSender) {
            this.mSmsSender = smsSender;
        }

        public void onDownloadMmsComplete(int i) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onDownloadMmsComplete call with result: " + i);
        }

        public void onFilterComplete(boolean z) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onFilterComplete call with result: " + z);
        }

        public void onSendMmsComplete(int i, byte[] bArr) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMmsComplete call with result: " + i);
        }

        public void onSendMultipartSmsComplete(int i, int[] iArr) {
            Rlog.e(SMSDispatcher.TAG, "Unexpected onSendMultipartSmsComplete call with result: " + i);
        }

        public void onSendSmsComplete(int i, int i2) {
            this.mSmsSender.disposeConnection(SMSDispatcher.this.mContext);
            SMSDispatcher.this.processSendSmsResponse(this.mSmsSender.mTracker, i, i2);
        }
    }

    protected static final class SmsTracker {
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

        private SmsTracker(HashMap<String, Object> hashMap, PendingIntent pendingIntent, PendingIntent pendingIntent2, PackageInfo packageInfo, String str, String str2, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, SmsHeader smsHeader, boolean z, String str3, int i, boolean z2, int i2) {
            this.mTimestamp = System.currentTimeMillis();
            this.mData = hashMap;
            this.mSentIntent = pendingIntent;
            this.mDeliveryIntent = pendingIntent2;
            this.mRetryCount = 0;
            this.mAppInfo = packageInfo;
            this.mDestAddress = str;
            this.mFormat = str2;
            this.mExpectMore = z;
            this.mImsRetry = 0;
            this.mMessageRef = 0;
            this.mvalidityPeriod = i2;
            this.mUnsentPartCount = atomicInteger;
            this.mAnyPartFailed = atomicBoolean;
            this.mMessageUri = uri;
            this.mSmsHeader = smsHeader;
            this.mFullMessageText = str3;
            this.mPhoneId = i;
            this.mIsText = z2;
        }

        private void persistOrUpdateMessage(Context context, int i, int i2) {
            if (this.mMessageUri != null) {
                updateMessageState(context, i, i2);
            } else {
                this.mMessageUri = persistSentMessageIfRequired(context, i, i2);
            }
        }

        private Uri persistSentMessageIfRequired(Context context, int i, int i2) {
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
            Rlog.d(SMSDispatcher.TAG, "Persist SMS into " + (i == 5 ? "FAILED" : "SENT"));
            ContentValues contentValues = new ContentValues();
            contentValues.put("phone_id", Integer.valueOf(this.mPhoneId));
            contentValues.put("address", this.mDestAddress);
            contentValues.put("body", this.mFullMessageText);
            contentValues.put("date", Long.valueOf(System.currentTimeMillis()));
            contentValues.put("seen", Integer.valueOf(1));
            contentValues.put("read", Integer.valueOf(1));
            CharSequence charSequence = this.mAppInfo != null ? this.mAppInfo.packageName : null;
            if (!TextUtils.isEmpty(charSequence)) {
                contentValues.put("creator", charSequence);
            }
            if (this.mDeliveryIntent != null) {
                contentValues.put(TextBasedSmsColumns.STATUS, Integer.valueOf(32));
            }
            if (i2 != 0) {
                contentValues.put(TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(i2));
            }
            long clearCallingIdentity = Binder.clearCallingIdentity();
            ContentResolver contentResolver = context.getContentResolver();
            try {
                Uri insert = contentResolver.insert(Sent.CONTENT_URI, contentValues);
                if (insert != null && i == 5) {
                    contentValues = new ContentValues(1);
                    contentValues.put("type", Integer.valueOf(5));
                    contentResolver.update(insert, contentValues, null, null);
                }
                Binder.restoreCallingIdentity(clearCallingIdentity);
                return insert;
            } catch (Exception e) {
                Rlog.e(SMSDispatcher.TAG, "writeOutboxMessage: Failed to persist outbox message", e);
                Binder.restoreCallingIdentity(clearCallingIdentity);
                return null;
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(clearCallingIdentity);
                throw th;
            }
        }

        private void updateMessageState(Context context, int i, int i2) {
            if (this.mMessageUri != null) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("type", Integer.valueOf(i));
                contentValues.put(TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(i2));
                long clearCallingIdentity = Binder.clearCallingIdentity();
                try {
                    if (SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, contentValues, null, null) != 1) {
                        Rlog.e(SMSDispatcher.TAG, "Failed to move message to " + i);
                    }
                    Binder.restoreCallingIdentity(clearCallingIdentity);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(clearCallingIdentity);
                }
            }
        }

        /* Access modifiers changed, original: 0000 */
        public boolean isMultipart() {
            return this.mData.containsKey("parts");
        }

        public void onFailed(Context context, int i, int i2) {
            boolean z = true;
            if (this.mAnyPartFailed != null) {
                this.mAnyPartFailed.set(true);
            }
            if (!(this.mUnsentPartCount == null || this.mUnsentPartCount.decrementAndGet() == 0)) {
                z = false;
            }
            if (z) {
                persistOrUpdateMessage(context, 5, i2);
            }
            if (this.mSentIntent != null) {
                try {
                    Intent intent = new Intent();
                    if (this.mMessageUri != null) {
                        intent.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (i2 != 0) {
                        intent.putExtra("errorCode", i2);
                    }
                    if (this.mUnsentPartCount != null && z) {
                        intent.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                    }
                    this.mSentIntent.send(context, i, intent);
                } catch (CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }

        public void onFailedMulti(Context context, int i, int i2) {
            Object obj;
            if (this.mUnsentPartCount != null) {
                obj = this.mUnsentPartCount.decrementAndGet() == 0 ? 1 : null;
            } else {
                int obj2 = 1;
            }
            if (obj2 != null) {
                persistOrUpdateMessage(context, 5, i2);
            }
            Iterator it = ((ArrayList) this.mData.get("sentIntents")).iterator();
            while (it.hasNext()) {
                PendingIntent pendingIntent = (PendingIntent) it.next();
                if (pendingIntent != null) {
                    try {
                        Intent intent = new Intent();
                        if (this.mMessageUri != null) {
                            intent.putExtra("uri", this.mMessageUri.toString());
                        }
                        if (i2 != 0) {
                            intent.putExtra("errorCode", i2);
                        }
                        if (!(this.mUnsentPartCount == null || obj2 == null)) {
                            intent.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                        }
                        pendingIntent.send(context, i, intent);
                    } catch (CanceledException e) {
                        Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                    }
                }
            }
            persistOrUpdateMessage(context, 5, i2);
        }

        public void onSent(Context context) {
            int i = this.mUnsentPartCount != null ? this.mUnsentPartCount.decrementAndGet() == 0 ? 1 : 0 : 1;
            if (i != 0) {
                int i2 = 2;
                if (this.mAnyPartFailed != null && this.mAnyPartFailed.get()) {
                    i2 = 5;
                }
                persistOrUpdateMessage(context, i2, 0);
            }
            if (this.mSentIntent != null) {
                try {
                    Intent intent = new Intent();
                    if (this.mMessageUri != null) {
                        intent.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (!(this.mUnsentPartCount == null || i == 0)) {
                        intent.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                    }
                    this.mSentIntent.send(context, -1, intent);
                } catch (CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }

        public void updateSentMessageStatus(Context context, int i) {
            if (this.mMessageUri != null) {
                ContentValues contentValues = new ContentValues(1);
                contentValues.put(TextBasedSmsColumns.STATUS, Integer.valueOf(i));
                SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, contentValues, null, null);
            }
        }
    }

    protected final class TextSmsSender extends SmsSender {
        public TextSmsSender(SmsTracker smsTracker) {
            super(smsTracker);
        }

        /* Access modifiers changed, original: protected */
        public void onServiceReady(ICarrierMessagingService iCarrierMessagingService) {
            String str = (String) this.mTracker.mData.get(Part.TEXT);
            if (str != null) {
                try {
                    iCarrierMessagingService.sendTextSms(str, SMSDispatcher.this.getSubId(), this.mTracker.mDestAddress, this.mSenderCallback);
                    return;
                } catch (RemoteException e) {
                    Rlog.e(SMSDispatcher.TAG, "Exception sending the SMS: " + e);
                    this.mSenderCallback.onSendSmsComplete(1, 0);
                    return;
                }
            }
            this.mSenderCallback.onSendSmsComplete(1, 0);
        }
    }

    protected SMSDispatcher(PhoneBase phoneBase, SmsUsageMonitor smsUsageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        this.mPhone = phoneBase;
        this.mImsSMSDispatcher = imsSMSDispatcher;
        this.mContext = phoneBase.getContext();
        this.mResolver = this.mContext.getContentResolver();
        this.mCi = phoneBase.mCi;
        this.mUsageMonitor = smsUsageMonitor;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mSettingsObserver = new SettingsObserver(this, this.mPremiumSmsRule, this.mContext);
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("sms_short_code_rule"), false, this.mSettingsObserver);
        this.mSmsCapable = this.mContext.getResources().getBoolean(17956949);
        this.mSmsSendDisabled = !SystemProperties.getBoolean("telephony.sms.send", this.mSmsCapable);
        Rlog.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + this.mSmsCapable + " format=" + getFormat() + " mSmsSendDisabled=" + this.mSmsSendDisabled);
    }

    private boolean denyIfQueueLimitReached(SmsTracker smsTracker) {
        if (this.mPendingTrackerCount >= 5) {
            Rlog.e(TAG, "Denied because queue limit reached");
            smsTracker.onFailed(this.mContext, 5, 0);
            return true;
        }
        this.mPendingTrackerCount++;
        return false;
    }

    private CharSequence getAppLabel(String str) {
        PackageManager packageManager = this.mContext.getPackageManager();
        try {
            return packageManager.getApplicationInfo(str, 0).loadLabel(packageManager);
        } catch (NameNotFoundException e) {
            Rlog.e(TAG, "PackageManager Name Not Found for package " + str);
            return str;
        }
    }

    private String getMultipartMessageText(ArrayList<String> arrayList) {
        StringBuilder stringBuilder = new StringBuilder();
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            String str = (String) it.next();
            if (str != null) {
                stringBuilder.append(str);
            }
        }
        return stringBuilder.toString();
    }

    private SmsTracker getNewSubmitPduTrackerForFirstPart(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4, SmsTracker smsTracker) {
        SubmitPdu submitPdu = SmsMessage.getSubmitPdu(str2, str, str3, pendingIntent2 != null, SmsHeader.toByteArray(smsHeader), i, smsHeader.languageTable, smsHeader.languageShiftTable);
        if (submitPdu != null) {
            HashMap smsTrackerMap = getSmsTrackerMap(str, str2, str3, submitPdu);
            String format = getFormat();
            boolean z3 = !z || z2;
            SmsTracker smsTracker2 = getSmsTracker(smsTrackerMap, pendingIntent, pendingIntent2, format, atomicInteger, atomicBoolean, uri, smsHeader, z3, str4, true, i3);
            ConcatRef concatRef = smsHeader.concatRef;
            boolean z4 = !z && concatRef.seqNumber == 1;
            smsTracker2.mFirstOfMultiPart = z4;
            smsTracker2.mMsgCount = concatRef.msgCount;
            smsTracker2.mSmsTracker = smsTracker;
            smsTracker2.mSendConfirmed = this.mSendConfirmed;
            return smsTracker2;
        }
        Rlog.e(TAG, "SMSDispatcher.sendNewSubmitPduForFirstPart(): getSubmitPdu() returned null");
        return null;
    }

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef++;
        return sConcatenatedRef;
    }

    protected static int getNotInServiceError(int i) {
        return i == 3 ? 2 : 4;
    }

    private String getOperatorNumeric() {
        String str;
        String str2;
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == null) {
            str = null;
            str2 = "IccRecords == null";
        } else {
            str = iccRecords.getOperatorNumeric();
            str2 = "IccRecords";
        }
        Rlog.d(TAG, "getOperatorNumeric = " + str + " from " + str2);
        return str;
    }

    protected static void handleNotInService(int i, PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return;
        }
        if (i == 3) {
            try {
                pendingIntent.send(2);
                return;
            } catch (CanceledException e) {
                return;
            }
        }
        pendingIntent.send(4);
    }

    private boolean isTestSim() {
        boolean z = false;
        if (TEST_SIM.equals(getOperatorNumeric())) {
            z = true;
        }
        Rlog.d(TAG, "isTestSim ret=" + z);
        return z;
    }

    private void processSendSmsResponse(SmsTracker smsTracker, int i, int i2) {
        if (smsTracker == null) {
            Rlog.e(TAG, "processSendSmsResponse: null tracker");
            return;
        }
        SmsResponse smsResponse = new SmsResponse(i2, null, -1);
        switch (i) {
            case 0:
                Rlog.d(TAG, "Sending SMS by IP succeeded.");
                sendMessage(obtainMessage(2, new AsyncResult(smsTracker, smsResponse, null)));
                return;
            case 1:
                Rlog.d(TAG, "Sending SMS by IP failed. Retry on carrier network.");
                sendSubmitPdu(smsTracker);
                return;
            case 2:
                Rlog.d(TAG, "Sending SMS by IP failed.");
                sendMessage(obtainMessage(2, new AsyncResult(smsTracker, smsResponse, new CommandException(Error.GENERIC_FAILURE))));
                return;
            default:
                Rlog.d(TAG, "Unknown result " + i + " Retry on carrier network.");
                sendSubmitPdu(smsTracker);
                return;
        }
    }

    private void sendMultipartSms(SmsTracker smsTracker) {
        HashMap hashMap = smsTracker.mData;
        String str = (String) hashMap.get("destAddr");
        String str2 = (String) hashMap.get("scaddress");
        ArrayList arrayList = (ArrayList) hashMap.get("parts");
        ArrayList arrayList2 = (ArrayList) hashMap.get("sentIntents");
        ArrayList arrayList3 = (ArrayList) hashMap.get("deliveryIntents");
        int state = this.mPhone.getServiceState().getState();
        int voiceRegState = this.mPhone.getServiceState().getVoiceRegState();
        int dataRegState = this.mPhone.getServiceState().getDataRegState();
        Rlog.d(TAG, "isIms = " + isIms() + ", ssVoice = " + voiceRegState + ", ssData = " + dataRegState);
        if (isIms() || voiceRegState == 0 || dataRegState == 0) {
            this.mSendConfirmed = smsTracker.mSendConfirmed;
            this.mOriginalAppInfo = smsTracker.mAppInfo;
            sendMultipartText(str, str2, arrayList, arrayList2, arrayList3, null, null, -1, smsTracker.mExpectMore, smsTracker.mvalidityPeriod);
            this.mSendConfirmed = false;
            return;
        }
        int size = arrayList.size();
        int i = 0;
        while (i < size) {
            PendingIntent pendingIntent = (arrayList2 == null || arrayList2.size() <= i) ? null : (PendingIntent) arrayList2.get(i);
            handleNotInService(state, pendingIntent);
            i++;
        }
    }

    public abstract TextEncodingDetails calculateLength(CharSequence charSequence, boolean z);

    /* Access modifiers changed, original: 0000 */
    public boolean checkDestination(SmsTracker smsTracker) {
        if (this.mContext.checkCallingOrSelfPermission(SEND_RESPOND_VIA_MESSAGE_PERMISSION) != 0) {
            String simCountryIso;
            int checkDestination;
            int i = this.mPremiumSmsRule.get();
            if (i == 1 || i == 3) {
                simCountryIso = this.mTelephonyManager.getSimCountryIso();
                if (simCountryIso == null || simCountryIso.length() != 2) {
                    Rlog.e(TAG, "Can't get SIM country Iso: trying network country Iso");
                    simCountryIso = this.mTelephonyManager.getNetworkCountryIso();
                }
                checkDestination = this.mUsageMonitor.checkDestination(smsTracker.mDestAddress, simCountryIso);
            } else {
                checkDestination = 0;
            }
            if (i == 2 || i == 3) {
                simCountryIso = this.mTelephonyManager.getNetworkCountryIso();
                if (simCountryIso == null || simCountryIso.length() != 2) {
                    Rlog.e(TAG, "Can't get Network country Iso: trying SIM country Iso");
                    simCountryIso = this.mTelephonyManager.getSimCountryIso();
                }
                checkDestination = SmsUsageMonitor.mergeShortCodeCategories(checkDestination, this.mUsageMonitor.checkDestination(smsTracker.mDestAddress, simCountryIso));
            }
            if (!(checkDestination == 0 || checkDestination == 1 || checkDestination == 2)) {
                int premiumSmsPermission = this.mUsageMonitor.getPremiumSmsPermission(smsTracker.mAppInfo.packageName);
                if (premiumSmsPermission == 0) {
                    premiumSmsPermission = 1;
                }
                switch (premiumSmsPermission) {
                    case 2:
                        Rlog.w(TAG, "User denied this app from sending to premium SMS");
                        sendMessage(obtainMessage(7, smsTracker));
                        return false;
                    case 3:
                        Rlog.d(TAG, "User approved this app to send to premium SMS");
                        return true;
                    default:
                        premiumSmsPermission = checkDestination == 3 ? 8 : 9;
                        if (getFormat() != android.telephony.SmsMessage.FORMAT_3GPP) {
                            sendMessage(obtainMessage(premiumSmsPermission, smsTracker));
                        } else if (smsTracker.mMsgCount == 0) {
                            sendMessage(obtainMessage(premiumSmsPermission, smsTracker));
                        } else if (smsTracker.mFirstOfMultiPart && !smsTracker.mSendConfirmed) {
                            if (smsTracker.mSmsTracker == null) {
                                Rlog.d(TAG, "Comfirm send to premium short code");
                                sendMessage(obtainMessage(premiumSmsPermission, smsTracker));
                            } else {
                                Rlog.d(TAG, "Comfirm send to premium multipart short code");
                                sendMessage(obtainMessage(premiumSmsPermission, smsTracker.mSmsTracker));
                            }
                            this.mConfirmation = true;
                        } else if (smsTracker.mSendConfirmed) {
                            Rlog.d(TAG, "already confirmed.");
                            return true;
                        }
                        return false;
                }
            }
        }
        return true;
    }

    public void dispose() {
        this.mContext.getContentResolver().unregisterContentObserver(this.mSettingsObserver);
    }

    /* Access modifiers changed, original: protected */
    public String getCarrierAppPackageName() {
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(this.mPhone.getPhoneId());
        if (uiccCard != null) {
            List carrierPackageNamesForIntent = uiccCard.getCarrierPackageNamesForIntent(this.mContext.getPackageManager(), new Intent("android.service.carrier.CarrierMessagingService"));
            if (carrierPackageNamesForIntent != null && carrierPackageNamesForIntent.size() == 1) {
                return (String) carrierPackageNamesForIntent.get(0);
            }
        }
        return null;
    }

    public abstract String getFormat();

    public String getImsSmsFormat() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.getImsSmsFormat();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return null;
    }

    public abstract SmsTracker getNewSubmitPduTracker(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4);

    public int getPremiumSmsPermission(String str) {
        return this.mUsageMonitor.getPremiumSmsPermission(str);
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getSmsTracker(HashMap<String, Object> hashMap, PendingIntent pendingIntent, PendingIntent pendingIntent2, String str, Uri uri, boolean z, String str2, boolean z2) {
        return getSmsTracker(hashMap, pendingIntent, pendingIntent2, str, null, null, uri, null, z, str2, z2, -1);
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getSmsTracker(HashMap<String, Object> hashMap, PendingIntent pendingIntent, PendingIntent pendingIntent2, String str, Uri uri, boolean z, String str2, boolean z2, int i) {
        return getSmsTracker(hashMap, pendingIntent, pendingIntent2, str, null, null, uri, null, z, str2, z2, i);
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getSmsTracker(HashMap<String, Object> hashMap, PendingIntent pendingIntent, PendingIntent pendingIntent2, String str, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, SmsHeader smsHeader, boolean z, String str2, boolean z2, int i) {
        PackageManager packageManager = this.mContext.getPackageManager();
        String[] packagesForUid = packageManager.getPackagesForUid(Binder.getCallingUid());
        PackageInfo packageInfo = null;
        if (packagesForUid != null && packagesForUid.length > 0) {
            try {
                packageInfo = packageManager.getPackageInfo(packagesForUid[0], 64);
            } catch (NameNotFoundException e) {
            }
        }
        return new SmsTracker(hashMap, pendingIntent, pendingIntent2, packageInfo, PhoneNumberUtils.extractNetworkPortion((String) hashMap.get("destAddr")), str, atomicInteger, atomicBoolean, uri, smsHeader, z, str2, this.mPhone.getPhoneId(), z2, i);
    }

    /* Access modifiers changed, original: protected */
    public HashMap<String, Object> getSmsTrackerMap(String str, String str2, int i, int i2, byte[] bArr, SubmitPduBase submitPduBase) {
        HashMap hashMap = new HashMap();
        hashMap.put("destAddr", str);
        hashMap.put("scAddr", str2);
        hashMap.put("destPort", Integer.valueOf(i));
        hashMap.put("origPort", Integer.valueOf(i2));
        hashMap.put("data", bArr);
        hashMap.put("smsc", submitPduBase.encodedScAddress);
        hashMap.put("pdu", submitPduBase.encodedMessage);
        return hashMap;
    }

    /* Access modifiers changed, original: protected */
    public HashMap<String, Object> getSmsTrackerMap(String str, String str2, String str3, SubmitPduBase submitPduBase) {
        HashMap hashMap = new HashMap();
        hashMap.put("destAddr", str);
        hashMap.put("scAddr", str2);
        hashMap.put(Part.TEXT, str3);
        hashMap.put("smsc", submitPduBase.encodedScAddress);
        hashMap.put("pdu", submitPduBase.encodedMessage);
        return hashMap;
    }

    /* Access modifiers changed, original: protected */
    public HashMap getSmsTrackerMap(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3) {
        HashMap hashMap = new HashMap();
        hashMap.put("destAddr", str);
        hashMap.put("scaddress", str2);
        hashMap.put("parts", arrayList);
        hashMap.put("sentIntents", arrayList2);
        hashMap.put("deliveryIntents", arrayList3);
        return hashMap;
    }

    /* Access modifiers changed, original: protected */
    public int getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhone.mPhoneId);
    }

    /* Access modifiers changed, original: protected */
    public void handleConfirmShortCode(boolean z, SmsTracker smsTracker) {
        if (!denyIfQueueLimitReached(smsTracker)) {
            int i = z ? 17040684 : 17040683;
            CharSequence appLabel = getAppLabel(smsTracker.mAppInfo.packageName);
            Resources system = Resources.getSystem();
            Spanned fromHtml = Html.fromHtml(system.getString(17040682, new Object[]{appLabel, smsTracker.mDestAddress}));
            View inflate = ((LayoutInflater) this.mContext.getSystemService("layout_inflater")).inflate(17367255, null);
            ConfirmDialogListener confirmDialogListener = new ConfirmDialogListener(smsTracker, (TextView) inflate.findViewById(16909222));
            ((TextView) inflate.findViewById(16909216)).setText(fromHtml);
            ((TextView) ((ViewGroup) inflate.findViewById(16909217)).findViewById(16909219)).setText(i);
            ((CheckBox) inflate.findViewById(16909220)).setOnCheckedChangeListener(confirmDialogListener);
            AlertDialog create = new Builder(this.mContext).setView(inflate).setPositiveButton(system.getString(17040685), confirmDialogListener).setNegativeButton(system.getString(17040686), confirmDialogListener).setOnCancelListener(confirmDialogListener).create();
            create.getWindow().setType(2003);
            create.show();
            confirmDialogListener.setPositiveButton(create.getButton(-1));
            confirmDialogListener.setNegativeButton(create.getButton(-2));
        }
    }

    public void handleMessage(Message message) {
        SmsTracker smsTracker;
        switch (message.what) {
            case 2:
                handleSendComplete((AsyncResult) message.obj);
                return;
            case 3:
                Rlog.d(TAG, "SMS retry..");
                sendRetrySms((SmsTracker) message.obj);
                return;
            case 4:
                handleReachSentLimit((SmsTracker) message.obj);
                return;
            case 5:
                smsTracker = (SmsTracker) message.obj;
                if (smsTracker.isMultipart()) {
                    smsTracker.mSendConfirmed = true;
                    sendMultipartSms(smsTracker);
                } else {
                    if (this.mPendingTrackerCount > 1) {
                        smsTracker.mExpectMore = true;
                    } else {
                        smsTracker.mExpectMore = false;
                    }
                    sendSms(smsTracker);
                }
                this.mPendingTrackerCount--;
                return;
            case 7:
                smsTracker = (SmsTracker) message.obj;
                if (smsTracker.isMultipart()) {
                    smsTracker.onFailedMulti(this.mContext, 5, 0);
                } else {
                    smsTracker.onFailed(this.mContext, 5, 0);
                }
                this.mPendingTrackerCount--;
                return;
            case 8:
                handleConfirmShortCode(false, (SmsTracker) message.obj);
                return;
            case 9:
                handleConfirmShortCode(true, (SmsTracker) message.obj);
                return;
            case 10:
                handleStatusReport(message.obj);
                return;
            default:
                Rlog.e(TAG, "handleMessage() ignoring message of unexpected type " + message.what);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleReachSentLimit(SmsTracker smsTracker) {
        if (!denyIfQueueLimitReached(smsTracker)) {
            CharSequence appLabel = getAppLabel(smsTracker.mAppInfo.packageName);
            Resources system = Resources.getSystem();
            Spanned fromHtml = Html.fromHtml(system.getString(17040679, new Object[]{appLabel}));
            ConfirmDialogListener confirmDialogListener = new ConfirmDialogListener(smsTracker, null);
            AlertDialog create = new Builder(this.mContext).setTitle(17040678).setIcon(17301642).setMessage(fromHtml).setPositiveButton(system.getString(17040680), confirmDialogListener).setNegativeButton(system.getString(17040681), confirmDialogListener).setOnCancelListener(confirmDialogListener).create();
            create.getWindow().setType(2003);
            create.show();
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleSendComplete(AsyncResult asyncResult) {
        int i = 0;
        SmsTracker smsTracker = (SmsTracker) asyncResult.userObj;
        PendingIntent pendingIntent = smsTracker.mSentIntent;
        if (asyncResult.result != null) {
            smsTracker.mMessageRef = ((SmsResponse) asyncResult.result).mMessageRef;
        } else {
            Rlog.d(TAG, "SmsResponse was null");
        }
        if (asyncResult.exception == null) {
            if (smsTracker.mDeliveryIntent != null) {
                this.deliveryPendingList.add(smsTracker);
            }
            smsTracker.onSent(this.mContext);
            return;
        }
        int state = this.mPhone.getServiceState().getState();
        int voiceRegState = this.mPhone.getServiceState().getVoiceRegState();
        int dataRegState = this.mPhone.getServiceState().getDataRegState();
        if (smsTracker.mImsRetry > 0 && voiceRegState != 0 && dataRegState != 0) {
            smsTracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  isIms()=" + isIms() + " mRetryCount=" + smsTracker.mRetryCount + " mImsRetry=" + smsTracker.mImsRetry + " mMessageRef=" + smsTracker.mMessageRef + " SSVoice= " + this.mPhone.getServiceState().getVoiceRegState() + " SSData= " + this.mPhone.getServiceState().getDataRegState() + " SS= " + this.mPhone.getServiceState().getState());
        } else if (isTestSim()) {
            smsTracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  because SIM be TestSIM");
        }
        if (!isIms() && voiceRegState != 0 && dataRegState != 0) {
            smsTracker.onFailed(this.mContext, getNotInServiceError(state), 0);
        } else if (((CommandException) asyncResult.exception).getCommandError() != Error.SMS_FAIL_RETRY || smsTracker.mRetryCount >= 3) {
            if (asyncResult.result != null) {
                i = ((SmsResponse) asyncResult.result).mErrorCode;
            }
            smsTracker.onFailed(this.mContext, ((CommandException) ((CommandException) asyncResult.exception)).getCommandError() == Error.FDN_CHECK_FAILURE ? 6 : 1, i);
        } else {
            smsTracker.mRetryCount++;
            sendMessageDelayed(obtainMessage(3, smsTracker), 2000);
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleStatusReport(Object obj) {
        Rlog.d(TAG, "handleStatusReport() called with no subclass.");
    }

    public abstract void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent);

    public boolean isIms() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.isIms();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public abstract void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2);

    /* Access modifiers changed, original: protected */
    public void sendMultipartText(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3, Uri uri, String str3, int i, boolean z, int i2) {
        int i3;
        String multipartMessageText = getMultipartMessageText(arrayList);
        int nextConcatenatedRef = getNextConcatenatedRef();
        int size = arrayList.size();
        int i4 = 0;
        TextEncodingDetails[] textEncodingDetailsArr = new TextEncodingDetails[size];
        int i5 = 0;
        while (true) {
            i3 = i5;
            if (i3 >= size) {
                break;
            }
            TextEncodingDetails calculateLength = calculateLength((CharSequence) arrayList.get(i3), false);
            if (i4 != calculateLength.codeUnitSize && (i4 == 0 || i4 == 1)) {
                i4 = calculateLength.codeUnitSize;
            }
            textEncodingDetailsArr[i3] = calculateLength;
            i5 = i3 + 1;
        }
        Object obj = new SmsTracker[size];
        AtomicInteger atomicInteger = new AtomicInteger(size);
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        i5 = 0;
        while (true) {
            int i6 = i5;
            if (i6 < size) {
                ConcatRef concatRef = new ConcatRef();
                concatRef.refNumber = nextConcatenatedRef & 255;
                concatRef.seqNumber = i6 + 1;
                concatRef.msgCount = size;
                concatRef.isEightBits = true;
                SmsHeader smsHeader = new SmsHeader();
                smsHeader.concatRef = concatRef;
                if (i4 == 1) {
                    smsHeader.languageTable = textEncodingDetailsArr[i6].languageTable;
                    smsHeader.languageShiftTable = textEncodingDetailsArr[i6].languageShiftTable;
                }
                PendingIntent pendingIntent = null;
                if (arrayList2 != null && arrayList2.size() > i6) {
                    pendingIntent = (PendingIntent) arrayList2.get(i6);
                }
                PendingIntent pendingIntent2 = null;
                if (arrayList3 != null && arrayList3.size() > i6) {
                    pendingIntent2 = (PendingIntent) arrayList3.get(i6);
                }
                if (getFormat() == android.telephony.SmsMessage.FORMAT_3GPP) {
                    if (i6 == 0) {
                        obj[i6] = getNewSubmitPduTrackerForFirstPart(str, str2, (String) arrayList.get(i6), smsHeader, i4, pendingIntent, pendingIntent2, i6 == size + -1, i, z, i2, atomicInteger, atomicBoolean, uri, multipartMessageText, getSmsTracker(getSmsTrackerMap(str, str2, arrayList, arrayList2, arrayList3), pendingIntent, pendingIntent2, getFormat(), uri, z, multipartMessageText, true));
                    } else {
                        obj[i6] = getNewSubmitPduTracker(str, str2, (String) arrayList.get(i6), smsHeader, i4, pendingIntent, pendingIntent2, i6 == size + -1, i, z, i2, atomicInteger, atomicBoolean, uri, multipartMessageText);
                    }
                    if (this.mConfirmation) {
                        this.mConfirmation = false;
                        return;
                    }
                } else {
                    obj[i6] = getNewSubmitPduTracker(str, str2, (String) arrayList.get(i6), smsHeader, i4, pendingIntent, pendingIntent2, i6 == size + -1, i, z, i2, atomicInteger, atomicBoolean, uri, multipartMessageText);
                }
                i5 = i6 + 1;
            } else {
                this.mOriginalAppInfo = null;
                if (arrayList == null || obj == null || obj.length == 0 || obj[0] == null) {
                    Rlog.e(TAG, "Cannot send multipart text. parts=" + arrayList + " trackers=" + obj);
                    return;
                }
                String carrierAppPackageName = getCarrierAppPackageName();
                if (carrierAppPackageName != null) {
                    Rlog.d(TAG, "Found carrier package.");
                    MultipartSmsSender multipartSmsSender = new MultipartSmsSender(arrayList, obj);
                    multipartSmsSender.sendSmsByCarrierApp(carrierAppPackageName, new MultipartSmsSenderCallback(multipartSmsSender));
                    return;
                }
                Rlog.v(TAG, "No carrier package.");
                for (SmsTracker smsTracker : obj) {
                    if (smsTracker != null) {
                        sendSubmitPdu(smsTracker);
                    } else {
                        Rlog.e(TAG, "Null tracker.");
                    }
                }
                return;
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void sendRawPdu(SmsTracker smsTracker) {
        byte[] bArr = (byte[]) smsTracker.mData.get("pdu");
        if (this.mSmsSendDisabled) {
            Rlog.e(TAG, "Device does not support sending sms.");
            smsTracker.onFailed(this.mContext, 4, 0);
        } else if (bArr == null) {
            Rlog.e(TAG, "Empty PDU");
            smsTracker.onFailed(this.mContext, 3, 0);
        } else {
            PackageManager packageManager = this.mContext.getPackageManager();
            String[] packagesForUid = packageManager.getPackagesForUid(Binder.getCallingUid());
            if (packagesForUid == null || packagesForUid.length == 0) {
                Rlog.e(TAG, "Can't get calling app package name: refusing to send SMS");
                smsTracker.onFailed(this.mContext, 1, 0);
                return;
            }
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packagesForUid[0], 64);
                if (checkDestination(smsTracker)) {
                    if (getFormat() == android.telephony.SmsMessage.FORMAT_3GPP) {
                        if (smsTracker.mMsgCount == 0) {
                            if (!this.mUsageMonitor.check(packageInfo.packageName, 1)) {
                                sendMessage(obtainMessage(4, smsTracker));
                                return;
                            }
                        } else if (!(!smsTracker.mFirstOfMultiPart || smsTracker.mSendConfirmed || this.mUsageMonitor.check(packageInfo.packageName, smsTracker.mMsgCount))) {
                            if (smsTracker.mSmsTracker == null) {
                                sendMessage(obtainMessage(4, smsTracker));
                            } else {
                                sendMessage(obtainMessage(4, smsTracker.mSmsTracker));
                            }
                            this.mConfirmation = true;
                            return;
                        }
                    } else if (!this.mUsageMonitor.check(packageInfo.packageName, 1)) {
                        sendMessage(obtainMessage(4, smsTracker));
                        return;
                    }
                    if (this.mOriginalAppInfo != null) {
                        smsTracker.mOriginalAppInfo = this.mOriginalAppInfo;
                    }
                    sendSms(smsTracker);
                }
            } catch (NameNotFoundException e) {
                Rlog.e(TAG, "Can't get calling app package info: refusing to send SMS");
                smsTracker.onFailed(this.mContext, 1, 0);
            }
        }
    }

    public void sendRetrySms(SmsTracker smsTracker) {
        if (this.mImsSMSDispatcher != null) {
            this.mImsSMSDispatcher.sendRetrySms(smsTracker);
        } else {
            Rlog.e(TAG, this.mImsSMSDispatcher + " is null. Retry failed");
        }
    }

    public abstract void sendSms(SmsTracker smsTracker);

    public abstract void sendSmsByPstn(SmsTracker smsTracker);

    public abstract void sendSubmitPdu(SmsTracker smsTracker);

    public abstract void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, int i, boolean z, int i2);

    public void setPremiumSmsPermission(String str, int i) {
        this.mUsageMonitor.setPremiumSmsPermission(str, i);
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        this.mUsageMonitor = phoneBase.mSmsUsageMonitor;
        Rlog.d(TAG, "Active phone changed to " + this.mPhone.getPhoneName());
    }
}
