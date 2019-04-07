package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.Parcel;
import android.telephony.Rlog;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

class RILRequest {
    static final String LOG_TAG = "RilRequest";
    private static final int MAX_POOL_SIZE = 4;
    static AtomicInteger sNextSerial = new AtomicInteger(0);
    private static RILRequest sPool = null;
    private static int sPoolSize = 0;
    private static Object sPoolSync = new Object();
    static Random sRandom = new Random();
    private Context mContext;
    RILRequest mNext;
    Parcel mParcel;
    int mRequest;
    Message mResult;
    int mSerial;

    private RILRequest() {
    }

    static RILRequest obtain(int i, Message message) {
        RILRequest rILRequest = null;
        synchronized (sPoolSync) {
            if (sPool != null) {
                rILRequest = sPool;
                sPool = rILRequest.mNext;
                rILRequest.mNext = null;
                sPoolSize--;
            }
        }
        if (rILRequest == null) {
            rILRequest = new RILRequest();
        }
        rILRequest.mSerial = sNextSerial.getAndIncrement();
        rILRequest.mRequest = i;
        rILRequest.mResult = message;
        rILRequest.mParcel = Parcel.obtain();
        if (message == null || message.getTarget() != null) {
            rILRequest.mParcel.writeInt(i);
            rILRequest.mParcel.writeInt(rILRequest.mSerial);
            return rILRequest;
        }
        throw new NullPointerException("Message target must not be null");
    }

    static void resetSerial() {
        sNextSerial.set(sRandom.nextInt());
    }

    /* Access modifiers changed, original: 0000 */
    public void onError(int i, Object obj) {
        CommandException fromRilErrno = CommandException.fromRilErrno(i);
        Rlog.d(LOG_TAG, serialString() + "< " + RIL.requestToString(this.mRequest) + " error: " + fromRilErrno + " ret=" + RIL.retToString(this.mRequest, obj));
        if (this.mResult != null) {
            AsyncResult.forMessage(this.mResult, obj, fromRilErrno);
            this.mResult.sendToTarget();
        }
        if (this.mParcel != null) {
            this.mParcel.recycle();
            this.mParcel = null;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void release() {
        synchronized (sPoolSync) {
            if (sPoolSize < 4) {
                this.mNext = sPool;
                sPool = this;
                sPoolSize++;
                this.mResult = null;
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public String serialString() {
        StringBuilder stringBuilder = new StringBuilder(8);
        String l = Long.toString((((long) this.mSerial) + 2147483648L) % 10000);
        stringBuilder.append('[');
        int length = l.length();
        for (int i = 0; i < 4 - length; i++) {
            stringBuilder.append('0');
        }
        stringBuilder.append(l);
        stringBuilder.append(']');
        return stringBuilder.toString();
    }
}
