package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;

public class SmsBroadcastUndelivered implements Runnable {
    private static final boolean DBG = true;
    private static final String[] PDU_PENDING_MESSAGE_PROJECTION = new String[]{"pdu", "sequence", "destination_port", "date", "reference_number", "count", "address", "_id"};
    private static final String TAG = "SmsBroadcastUndelivered";
    private static final Uri sRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    private final CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private final Context mContext;
    private final GsmInboundSmsHandler mGsmInboundSmsHandler;
    private final ContentResolver mResolver;

    private static class SmsReferenceKey {
        final String mAddress;
        final int mMessageCount;
        final int mReferenceNumber;

        SmsReferenceKey(InboundSmsTracker inboundSmsTracker) {
            this.mAddress = inboundSmsTracker.getAddress();
            this.mReferenceNumber = inboundSmsTracker.getReferenceNumber();
            this.mMessageCount = inboundSmsTracker.getMessageCount();
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof SmsReferenceKey)) {
                return false;
            }
            SmsReferenceKey smsReferenceKey = (SmsReferenceKey) obj;
            return smsReferenceKey.mAddress.equals(this.mAddress) && smsReferenceKey.mReferenceNumber == this.mReferenceNumber && smsReferenceKey.mMessageCount == this.mMessageCount;
        }

        /* Access modifiers changed, original: 0000 */
        public String[] getDeleteWhereArgs() {
            return new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
        }

        public int hashCode() {
            return (((this.mReferenceNumber * 31) + this.mMessageCount) * 31) + this.mAddress.hashCode();
        }
    }

    public SmsBroadcastUndelivered(Context context, GsmInboundSmsHandler gsmInboundSmsHandler, CdmaInboundSmsHandler cdmaInboundSmsHandler) {
        this.mContext = context;
        this.mResolver = context.getContentResolver();
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mCdmaInboundSmsHandler = cdmaInboundSmsHandler;
    }

    private void broadcastSms(InboundSmsTracker inboundSmsTracker) {
        InboundSmsHandler inboundSmsHandler = inboundSmsTracker.is3gpp2() ? this.mCdmaInboundSmsHandler : this.mGsmInboundSmsHandler;
        if (inboundSmsHandler != null) {
            inboundSmsHandler.sendMessage(2, inboundSmsTracker);
        } else {
            Rlog.e(TAG, "null handler for " + inboundSmsTracker.getFormat() + " format, can't deliver.");
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:32:0x00c5  */
    private void scanRawTable() {
        /*
        r18 = this;
        r10 = java.lang.System.nanoTime();
        r12 = new java.util.HashMap;
        r2 = 4;
        r12.<init>(r2);
        r13 = new java.util.HashSet;
        r2 = 4;
        r13.<init>(r2);
        r9 = 0;
        r8 = 0;
        r0 = r18;
        r2 = r0.mResolver;	 Catch:{ SQLException -> 0x01dc, all -> 0x01d8 }
        r3 = sRawUri;	 Catch:{ SQLException -> 0x01dc, all -> 0x01d8 }
        r4 = PDU_PENDING_MESSAGE_PROJECTION;	 Catch:{ SQLException -> 0x01dc, all -> 0x01d8 }
        r5 = 0;
        r6 = 0;
        r7 = 0;
        r3 = r2.query(r3, r4, r5, r6, r7);	 Catch:{ SQLException -> 0x01dc, all -> 0x01d8 }
        if (r3 != 0) goto L_0x0057;
    L_0x0023:
        r2 = "SmsBroadcastUndelivered";
        r4 = "error getting pending message cursor";
        android.telephony.Rlog.e(r2, r4);	 Catch:{ SQLException -> 0x0073 }
        if (r3 == 0) goto L_0x002f;
    L_0x002c:
        r3.close();
    L_0x002f:
        r2 = "SmsBroadcastUndelivered";
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "finished scanning raw table in ";
        r3 = r3.append(r4);
        r4 = java.lang.System.nanoTime();
        r4 = r4 - r10;
        r6 = 1000000; // 0xf4240 float:1.401298E-39 double:4.940656E-318;
        r4 = r4 / r6;
        r3 = r3.append(r4);
        r4 = " ms";
        r3 = r3.append(r4);
        r3 = r3.toString();
        android.telephony.Rlog.d(r2, r3);
    L_0x0056:
        return;
    L_0x0057:
        r4 = com.android.internal.telephony.InboundSmsHandler.isCurrentFormat3gpp2();	 Catch:{ SQLException -> 0x0073 }
    L_0x005b:
        r2 = r3.moveToNext();	 Catch:{ SQLException -> 0x0073 }
        if (r2 == 0) goto L_0x0155;
    L_0x0061:
        r5 = new com.android.internal.telephony.InboundSmsTracker;	 Catch:{ IllegalArgumentException -> 0x00a8 }
        r5.<init>(r3, r4);	 Catch:{ IllegalArgumentException -> 0x00a8 }
        r2 = r5.getMessageCount();	 Catch:{ SQLException -> 0x0073 }
        r6 = 1;
        if (r2 != r6) goto L_0x00f0;
    L_0x006d:
        r0 = r18;
        r0.broadcastSms(r5);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x005b;
    L_0x0073:
        r2 = move-exception;
    L_0x0074:
        r4 = "SmsBroadcastUndelivered";
        r5 = "error reading pending SMS messages";
        android.telephony.Rlog.e(r4, r5, r2);	 Catch:{ all -> 0x00c2 }
        if (r3 == 0) goto L_0x0080;
    L_0x007d:
        r3.close();
    L_0x0080:
        r2 = "SmsBroadcastUndelivered";
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "finished scanning raw table in ";
        r3 = r3.append(r4);
        r4 = java.lang.System.nanoTime();
        r4 = r4 - r10;
        r6 = 1000000; // 0xf4240 float:1.401298E-39 double:4.940656E-318;
        r4 = r4 / r6;
        r3 = r3.append(r4);
        r4 = " ms";
        r3 = r3.append(r4);
        r3 = r3.toString();
        android.telephony.Rlog.d(r2, r3);
        goto L_0x0056;
    L_0x00a8:
        r2 = move-exception;
        r5 = "SmsBroadcastUndelivered";
        r6 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0073 }
        r6.<init>();	 Catch:{ SQLException -> 0x0073 }
        r7 = "error loading SmsTracker: ";
        r6 = r6.append(r7);	 Catch:{ SQLException -> 0x0073 }
        r2 = r6.append(r2);	 Catch:{ SQLException -> 0x0073 }
        r2 = r2.toString();	 Catch:{ SQLException -> 0x0073 }
        android.telephony.Rlog.e(r5, r2);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x005b;
    L_0x00c2:
        r2 = move-exception;
    L_0x00c3:
        if (r3 == 0) goto L_0x00c8;
    L_0x00c5:
        r3.close();
    L_0x00c8:
        r3 = "SmsBroadcastUndelivered";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "finished scanning raw table in ";
        r4 = r4.append(r5);
        r6 = java.lang.System.nanoTime();
        r6 = r6 - r10;
        r8 = 1000000; // 0xf4240 float:1.401298E-39 double:4.940656E-318;
        r6 = r6 / r8;
        r4 = r4.append(r6);
        r5 = " ms";
        r4 = r4.append(r5);
        r4 = r4.toString();
        android.telephony.Rlog.d(r3, r4);
        throw r2;
    L_0x00f0:
        r6 = new com.android.internal.telephony.SmsBroadcastUndelivered$SmsReferenceKey;	 Catch:{ SQLException -> 0x0073 }
        r6.<init>(r5);	 Catch:{ SQLException -> 0x0073 }
        r2 = r12.get(r6);	 Catch:{ SQLException -> 0x0073 }
        r2 = (java.lang.Integer) r2;	 Catch:{ SQLException -> 0x0073 }
        r0 = r18;
        r7 = r0.mContext;	 Catch:{ SQLException -> 0x0073 }
        r7 = r7.getResources();	 Catch:{ SQLException -> 0x0073 }
        r8 = 17039467; // 0x104006b float:2.424487E-38 double:8.4186153E-317;
        r7 = r7.getString(r8);	 Catch:{ SQLException -> 0x0073 }
        r7 = java.lang.Long.valueOf(r7);	 Catch:{ SQLException -> 0x0073 }
        r8 = r7.longValue();	 Catch:{ SQLException -> 0x0073 }
        if (r2 != 0) goto L_0x012f;
    L_0x0114:
        r2 = 1;
        r2 = java.lang.Integer.valueOf(r2);	 Catch:{ SQLException -> 0x0073 }
        r12.put(r6, r2);	 Catch:{ SQLException -> 0x0073 }
        r14 = r5.getTimestamp();	 Catch:{ SQLException -> 0x0073 }
        r16 = java.lang.System.currentTimeMillis();	 Catch:{ SQLException -> 0x0073 }
        r8 = r16 - r8;
        r2 = (r14 > r8 ? 1 : (r14 == r8 ? 0 : -1));
        if (r2 >= 0) goto L_0x005b;
    L_0x012a:
        r13.add(r6);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x005b;
    L_0x012f:
        r2 = r2.intValue();	 Catch:{ SQLException -> 0x0073 }
        r2 = r2 + 1;
        r7 = r5.getMessageCount();	 Catch:{ SQLException -> 0x0073 }
        if (r2 != r7) goto L_0x014c;
    L_0x013b:
        r2 = "SmsBroadcastUndelivered";
        r7 = "found complete multi-part message";
        android.telephony.Rlog.d(r2, r7);	 Catch:{ SQLException -> 0x0073 }
        r0 = r18;
        r0.broadcastSms(r5);	 Catch:{ SQLException -> 0x0073 }
        r13.remove(r6);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x005b;
    L_0x014c:
        r2 = java.lang.Integer.valueOf(r2);	 Catch:{ SQLException -> 0x0073 }
        r12.put(r6, r2);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x005b;
    L_0x0155:
        r4 = r13.iterator();	 Catch:{ SQLException -> 0x0073 }
    L_0x0159:
        r2 = r4.hasNext();	 Catch:{ SQLException -> 0x0073 }
        if (r2 == 0) goto L_0x01aa;
    L_0x015f:
        r2 = r4.next();	 Catch:{ SQLException -> 0x0073 }
        r2 = (com.android.internal.telephony.SmsBroadcastUndelivered.SmsReferenceKey) r2;	 Catch:{ SQLException -> 0x0073 }
        r0 = r18;
        r5 = r0.mResolver;	 Catch:{ SQLException -> 0x0073 }
        r6 = sRawUri;	 Catch:{ SQLException -> 0x0073 }
        r7 = "address=? AND reference_number=? AND count=?";
        r8 = r2.getDeleteWhereArgs();	 Catch:{ SQLException -> 0x0073 }
        r5 = r5.delete(r6, r7, r8);	 Catch:{ SQLException -> 0x0073 }
        if (r5 != 0) goto L_0x017f;
    L_0x0177:
        r2 = "SmsBroadcastUndelivered";
        r5 = "No rows were deleted from raw table!";
        android.telephony.Rlog.e(r2, r5);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x0159;
    L_0x017f:
        r6 = "SmsBroadcastUndelivered";
        r7 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0073 }
        r7.<init>();	 Catch:{ SQLException -> 0x0073 }
        r8 = "Deleted ";
        r7 = r7.append(r8);	 Catch:{ SQLException -> 0x0073 }
        r5 = r7.append(r5);	 Catch:{ SQLException -> 0x0073 }
        r7 = " rows from raw table for incomplete ";
        r5 = r5.append(r7);	 Catch:{ SQLException -> 0x0073 }
        r2 = r2.mMessageCount;	 Catch:{ SQLException -> 0x0073 }
        r2 = r5.append(r2);	 Catch:{ SQLException -> 0x0073 }
        r5 = " part message";
        r2 = r2.append(r5);	 Catch:{ SQLException -> 0x0073 }
        r2 = r2.toString();	 Catch:{ SQLException -> 0x0073 }
        android.telephony.Rlog.d(r6, r2);	 Catch:{ SQLException -> 0x0073 }
        goto L_0x0159;
    L_0x01aa:
        if (r3 == 0) goto L_0x01af;
    L_0x01ac:
        r3.close();
    L_0x01af:
        r2 = "SmsBroadcastUndelivered";
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "finished scanning raw table in ";
        r3 = r3.append(r4);
        r4 = java.lang.System.nanoTime();
        r4 = r4 - r10;
        r6 = 1000000; // 0xf4240 float:1.401298E-39 double:4.940656E-318;
        r4 = r4 / r6;
        r3 = r3.append(r4);
        r4 = " ms";
        r3 = r3.append(r4);
        r3 = r3.toString();
        android.telephony.Rlog.d(r2, r3);
        goto L_0x0056;
    L_0x01d8:
        r2 = move-exception;
        r3 = r9;
        goto L_0x00c3;
    L_0x01dc:
        r2 = move-exception;
        r3 = r8;
        goto L_0x0074;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsBroadcastUndelivered.scanRawTable():void");
    }

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
}
