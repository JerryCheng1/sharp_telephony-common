package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SmsBroadcastUndelivered implements Runnable {
    private static final boolean DBG = true;
    private static final String TAG = "SmsBroadcastUndelivered";
    private final CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private final Context mContext;
    private final GsmInboundSmsHandler mGsmInboundSmsHandler;
    private final ContentResolver mResolver;
    private static final String[] PDU_PENDING_MESSAGE_PROJECTION = {"pdu", "sequence", "destination_port", "date", "reference_number", "count", "address", "_id"};
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    public SmsBroadcastUndelivered(Context context, GsmInboundSmsHandler gsmInboundSmsHandler, CdmaInboundSmsHandler cdmaInboundSmsHandler) {
        this.mContext = context;
        this.mResolver = context.getContentResolver();
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mCdmaInboundSmsHandler = cdmaInboundSmsHandler;
    }

    @Override // java.lang.Runnable
    public void run() {
        Rlog.d(TAG, "scanning raw table for undelivered messages");
        scanRawTable();
        if (this.mGsmInboundSmsHandler != null) {
            this.mGsmInboundSmsHandler.sendMessage(6);
        }
        if (this.mCdmaInboundSmsHandler != null) {
            this.mCdmaInboundSmsHandler.sendMessage(6);
        }
    }

    private void scanRawTable() {
        long startTime;
        Cursor cursor;
        try {
            startTime = System.nanoTime();
            HashMap<SmsReferenceKey, Integer> multiPartReceivedCount = new HashMap<>(4);
            HashSet<SmsReferenceKey> oldMultiPartMessages = new HashSet<>(4);
            cursor = null;
            try {
                Cursor cursor2 = this.mResolver.query(sRawUri, PDU_PENDING_MESSAGE_PROJECTION, null, null, null);
                if (cursor2 == null) {
                    Rlog.e(TAG, "error getting pending message cursor");
                    if (cursor2 != null) {
                        cursor2.close();
                    }
                    Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
                    return;
                }
                boolean isCurrentFormat3gpp2 = InboundSmsHandler.isCurrentFormat3gpp2();
                while (cursor2.moveToNext()) {
                    try {
                        InboundSmsTracker tracker = new InboundSmsTracker(cursor2, isCurrentFormat3gpp2);
                        if (tracker.getMessageCount() == 1) {
                            broadcastSms(tracker);
                        } else {
                            SmsReferenceKey reference = new SmsReferenceKey(tracker);
                            Integer receivedCount = multiPartReceivedCount.get(reference);
                            long expireAge = Long.valueOf(this.mContext.getResources().getString(17039467)).longValue();
                            if (receivedCount == null) {
                                multiPartReceivedCount.put(reference, 1);
                                if (tracker.getTimestamp() < System.currentTimeMillis() - expireAge) {
                                    oldMultiPartMessages.add(reference);
                                }
                            } else {
                                int newCount = receivedCount.intValue() + 1;
                                if (newCount == tracker.getMessageCount()) {
                                    Rlog.d(TAG, "found complete multi-part message");
                                    broadcastSms(tracker);
                                    oldMultiPartMessages.remove(reference);
                                } else {
                                    multiPartReceivedCount.put(reference, Integer.valueOf(newCount));
                                }
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        Rlog.e(TAG, "error loading SmsTracker: " + e);
                    }
                }
                Iterator i$ = oldMultiPartMessages.iterator();
                while (i$.hasNext()) {
                    SmsReferenceKey message = i$.next();
                    int rows = this.mResolver.delete(sRawUri, "address=? AND reference_number=? AND count=?", message.getDeleteWhereArgs());
                    if (rows == 0) {
                        Rlog.e(TAG, "No rows were deleted from raw table!");
                    } else {
                        Rlog.d(TAG, "Deleted " + rows + " rows from raw table for incomplete " + message.mMessageCount + " part message");
                    }
                }
                if (cursor2 != null) {
                    cursor2.close();
                }
                Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
            } catch (SQLException e2) {
                Rlog.e(TAG, "error reading pending SMS messages", e2);
                if (0 != 0) {
                    cursor.close();
                }
                Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
            }
        } catch (Throwable th) {
            if (0 != 0) {
                cursor.close();
            }
            Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
            throw th;
        }
    }

    private void broadcastSms(InboundSmsTracker tracker) {
        InboundSmsHandler handler;
        if (tracker.is3gpp2()) {
            handler = this.mCdmaInboundSmsHandler;
        } else {
            handler = this.mGsmInboundSmsHandler;
        }
        if (handler != null) {
            handler.sendMessage(2, tracker);
        } else {
            Rlog.e(TAG, "null handler for " + tracker.getFormat() + " format, can't deliver.");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class SmsReferenceKey {
        final String mAddress;
        final int mMessageCount;
        final int mReferenceNumber;

        SmsReferenceKey(InboundSmsTracker tracker) {
            this.mAddress = tracker.getAddress();
            this.mReferenceNumber = tracker.getReferenceNumber();
            this.mMessageCount = tracker.getMessageCount();
        }

        String[] getDeleteWhereArgs() {
            return new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
        }

        public int hashCode() {
            return (((this.mReferenceNumber * 31) + this.mMessageCount) * 31) + this.mAddress.hashCode();
        }

        public boolean equals(Object o) {
            if (!(o instanceof SmsReferenceKey)) {
                return false;
            }
            SmsReferenceKey other = (SmsReferenceKey) o;
            return other.mAddress.equals(this.mAddress) && other.mReferenceNumber == this.mReferenceNumber && other.mMessageCount == this.mMessageCount;
        }
    }
}
