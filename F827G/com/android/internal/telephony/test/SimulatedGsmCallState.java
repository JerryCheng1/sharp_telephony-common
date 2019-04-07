package com.android.internal.telephony.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.DriverCall;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

class SimulatedGsmCallState extends Handler {
    static final int CONNECTING_PAUSE_MSEC = 500;
    static final int EVENT_PROGRESS_CALL_STATE = 1;
    static final int MAX_CALLS = 7;
    private boolean mAutoProgressConnecting = true;
    CallInfo[] mCalls = new CallInfo[7];
    private boolean mNextDialFailImmediately;

    public SimulatedGsmCallState(Looper looper) {
        super(looper);
    }

    private int countActiveLines() throws InvalidStateEx {
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        int i5 = 0;
        Object obj = null;
        for (CallInfo callInfo : this.mCalls) {
            if (callInfo != null) {
                if (i == 0 && callInfo.mIsMpty) {
                    obj = callInfo.mState == State.HOLDING ? 1 : null;
                } else if (callInfo.mIsMpty && obj != null && callInfo.mState == State.ACTIVE) {
                    Rlog.e("ModelInterpreter", "Invalid state");
                    throw new InvalidStateEx();
                } else if (!(callInfo.mIsMpty || i == 0 || obj == null || callInfo.mState != State.HOLDING)) {
                    Rlog.e("ModelInterpreter", "Invalid state");
                    throw new InvalidStateEx();
                }
                i2 |= callInfo.mState == State.HOLDING ? 1 : 0;
                i3 |= callInfo.mState == State.ACTIVE ? 1 : 0;
                i4 |= callInfo.isConnecting();
                i5 |= callInfo.isRinging();
                i = callInfo.mIsMpty | i;
            }
        }
        i = i2 != 0 ? 1 : 0;
        if (i3 != 0) {
            i++;
        }
        if (i4 != 0) {
            i++;
        }
        return i5 != 0 ? i + 1 : i;
    }

    public boolean conference() {
        int i = 0;
        int i2 = 0;
        for (CallInfo callInfo : this.mCalls) {
            if (callInfo != null) {
                i2++;
                if (callInfo.isConnecting()) {
                    return false;
                }
            }
        }
        while (i < this.mCalls.length) {
            CallInfo callInfo2 = this.mCalls[i];
            if (callInfo2 != null) {
                callInfo2.mState = State.ACTIVE;
                if (i2 > 0) {
                    callInfo2.mIsMpty = true;
                }
            }
            i++;
        }
        return true;
    }

    public boolean explicitCallTransfer() {
        int i = 0;
        for (CallInfo callInfo : this.mCalls) {
            if (callInfo != null) {
                i++;
                if (callInfo.isConnecting()) {
                    return false;
                }
            }
        }
        return triggerHangupAll();
    }

    public List<String> getClccLines() {
        ArrayList arrayList = new ArrayList(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo callInfo = this.mCalls[i];
            if (callInfo != null) {
                arrayList.add(callInfo.toCLCCLine(i + 1));
            }
        }
        return arrayList;
    }

    public List<DriverCall> getDriverCalls() {
        ArrayList arrayList = new ArrayList(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo callInfo = this.mCalls[i];
            if (callInfo != null) {
                arrayList.add(callInfo.toDriverCall(i + 1));
            }
        }
        Rlog.d("GSM", "SC< getDriverCalls " + arrayList);
        return arrayList;
    }

    public void handleMessage(Message message) {
        synchronized (this) {
            switch (message.what) {
                case 1:
                    progressConnectingCallState();
                    break;
            }
        }
    }

    public boolean onAnswer() {
        boolean z = false;
        synchronized (this) {
            for (CallInfo callInfo : this.mCalls) {
                if (callInfo != null && (callInfo.mState == State.INCOMING || callInfo.mState == State.WAITING)) {
                    z = switchActiveAndHeldOrWaiting();
                    break;
                }
            }
        }
        return z;
    }

    public boolean onChld(char c, char c2) {
        int i;
        if (c2 != 0) {
            i = c2 - 49;
            if (i < 0 || i >= this.mCalls.length) {
                return false;
            }
        }
        i = 0;
        switch (c) {
            case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /*48*/:
                return releaseHeldOrUDUB();
            case '1':
                if (c2 <= 0) {
                    return releaseActiveAcceptHeldOrWaiting();
                }
                if (this.mCalls[i] == null) {
                    return false;
                }
                this.mCalls[i] = null;
                return true;
            case OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /*50*/:
                return c2 <= 0 ? switchActiveAndHeldOrWaiting() : separateCall(i);
            case '3':
                return conference();
            case '4':
                return explicitCallTransfer();
            default:
                return false;
        }
    }

    public boolean onDial(String str) {
        int i = -1;
        Rlog.d("GSM", "SC> dial '" + str + "'");
        if (this.mNextDialFailImmediately) {
            this.mNextDialFailImmediately = false;
            Rlog.d("GSM", "SC< dial fail (per request)");
            return false;
        }
        String extractNetworkPortion = PhoneNumberUtils.extractNetworkPortion(str);
        if (extractNetworkPortion.length() == 0) {
            Rlog.d("GSM", "SC< dial fail (invalid ph num)");
            return false;
        } else if (extractNetworkPortion.startsWith("*99") && extractNetworkPortion.endsWith("#")) {
            Rlog.d("GSM", "SC< dial ignored (gprs)");
            return true;
        } else {
            try {
                if (countActiveLines() > 1) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                int i2 = 0;
                while (i2 < this.mCalls.length) {
                    if (i < 0 && this.mCalls[i2] == null) {
                        i = i2;
                    }
                    if (this.mCalls[i2] == null || this.mCalls[i2].isActiveOrHeld()) {
                        if (this.mCalls[i2] != null && this.mCalls[i2].mState == State.ACTIVE) {
                            this.mCalls[i2].mState = State.HOLDING;
                        }
                        i2++;
                    } else {
                        Rlog.d("GSM", "SC< dial fail (invalid call state)");
                        return false;
                    }
                }
                if (i < 0) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                this.mCalls[i] = CallInfo.createOutgoingCall(extractNetworkPortion);
                if (this.mAutoProgressConnecting) {
                    sendMessageDelayed(obtainMessage(1, this.mCalls[i]), 500);
                }
                Rlog.d("GSM", "SC< dial (slot = " + i + ")");
                return true;
            } catch (InvalidStateEx e) {
                Rlog.d("GSM", "SC< dial fail (invalid call state)");
                return false;
            }
        }
    }

    public boolean onHangup() {
        int i = 0;
        boolean z = false;
        while (true) {
            int i2 = i;
            if (i2 >= this.mCalls.length) {
                return z;
            }
            CallInfo callInfo = this.mCalls[i2];
            if (!(callInfo == null || callInfo.mState == State.WAITING)) {
                this.mCalls[i2] = null;
                z = true;
            }
            i = i2 + 1;
        }
    }

    public void progressConnectingCallState() {
        synchronized (this) {
            int i = 0;
            while (i < this.mCalls.length) {
                CallInfo callInfo = this.mCalls[i];
                if (callInfo == null || callInfo.mState != State.DIALING) {
                    if (callInfo != null && callInfo.mState == State.ALERTING) {
                        callInfo.mState = State.ACTIVE;
                        break;
                    }
                    i++;
                } else {
                    callInfo.mState = State.ALERTING;
                    if (this.mAutoProgressConnecting) {
                        sendMessageDelayed(obtainMessage(1, callInfo), 500);
                    }
                }
            }
        }
    }

    public void progressConnectingToActive() {
        synchronized (this) {
            for (CallInfo callInfo : this.mCalls) {
                if (callInfo != null && (callInfo.mState == State.DIALING || callInfo.mState == State.ALERTING)) {
                    callInfo.mState = State.ACTIVE;
                    break;
                }
            }
        }
    }

    public boolean releaseActiveAcceptHeldOrWaiting() {
        int i;
        CallInfo callInfo;
        int i2 = 0;
        int i3 = 0;
        for (i = 0; i < this.mCalls.length; i++) {
            callInfo = this.mCalls[i];
            if (callInfo != null && callInfo.mState == State.ACTIVE) {
                this.mCalls[i] = null;
                i3 = true;
            }
        }
        if (i3 == 0) {
            for (i3 = 0; i3 < this.mCalls.length; i3++) {
                CallInfo callInfo2 = this.mCalls[i3];
                if (callInfo2 != null && (callInfo2.mState == State.DIALING || callInfo2.mState == State.ALERTING)) {
                    this.mCalls[i3] = null;
                }
            }
        }
        i3 = 0;
        for (CallInfo callInfo3 : this.mCalls) {
            if (callInfo3 != null && callInfo3.mState == State.HOLDING) {
                callInfo3.mState = State.ACTIVE;
                i3 = true;
            }
        }
        if (i3 == 0) {
            while (i2 < this.mCalls.length) {
                CallInfo callInfo4 = this.mCalls[i2];
                if (callInfo4 != null && callInfo4.isRinging()) {
                    callInfo4.mState = State.ACTIVE;
                    break;
                }
                i2++;
            }
        }
        return true;
    }

    public boolean releaseHeldOrUDUB() {
        boolean z;
        int i = 0;
        for (int i2 = 0; i2 < this.mCalls.length; i2++) {
            CallInfo callInfo = this.mCalls[i2];
            if (callInfo != null && callInfo.isRinging()) {
                this.mCalls[i2] = null;
                z = true;
                break;
            }
        }
        z = false;
        if (!z) {
            while (i < this.mCalls.length) {
                CallInfo callInfo2 = this.mCalls[i];
                if (callInfo2 != null && callInfo2.mState == State.HOLDING) {
                    this.mCalls[i] = null;
                }
                i++;
            }
        }
        return true;
    }

    /* JADX WARNING: Removed duplicated region for block: B:25:0x003f A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x0038 A:{Catch:{ InvalidStateEx -> 0x0042 }} */
    public boolean separateCall(int r7) {
        /*
        r6 = this;
        r2 = 1;
        r1 = 0;
        r0 = r6.mCalls;	 Catch:{ InvalidStateEx -> 0x0042 }
        r0 = r0[r7];	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r0 == 0) goto L_0x0014;
    L_0x0008:
        r3 = r0.isConnecting();	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r3 != 0) goto L_0x0014;
    L_0x000e:
        r3 = r6.countActiveLines();	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r3 == r2) goto L_0x0015;
    L_0x0014:
        return r1;
    L_0x0015:
        r3 = com.android.internal.telephony.test.CallInfo.State.ACTIVE;	 Catch:{ InvalidStateEx -> 0x0042 }
        r0.mState = r3;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3 = 0;
        r0.mIsMpty = r3;	 Catch:{ InvalidStateEx -> 0x0042 }
        r0 = r1;
    L_0x001d:
        r3 = r6.mCalls;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3 = r3.length;	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r0 >= r3) goto L_0x0047;
    L_0x0022:
        if (r0 == r7) goto L_0x0044;
    L_0x0024:
        r3 = r6.mCalls;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3 = r3[r0];	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r3 == 0) goto L_0x0044;
    L_0x002a:
        r4 = r3.mState;	 Catch:{ InvalidStateEx -> 0x0042 }
        r5 = com.android.internal.telephony.test.CallInfo.State.ACTIVE;	 Catch:{ InvalidStateEx -> 0x0042 }
        if (r4 != r5) goto L_0x0044;
    L_0x0030:
        r4 = com.android.internal.telephony.test.CallInfo.State.HOLDING;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3.mState = r4;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3 = r2;
        r4 = r0;
    L_0x0036:
        if (r3 != r2) goto L_0x003f;
    L_0x0038:
        r3 = r6.mCalls;	 Catch:{ InvalidStateEx -> 0x0042 }
        r3 = r3[r4];	 Catch:{ InvalidStateEx -> 0x0042 }
        r4 = 0;
        r3.mIsMpty = r4;	 Catch:{ InvalidStateEx -> 0x0042 }
    L_0x003f:
        r0 = r0 + 1;
        goto L_0x001d;
    L_0x0042:
        r0 = move-exception;
        goto L_0x0014;
    L_0x0044:
        r3 = r1;
        r4 = r1;
        goto L_0x0036;
    L_0x0047:
        r1 = r2;
        goto L_0x0014;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.test.SimulatedGsmCallState.separateCall(int):boolean");
    }

    public void setAutoProgressConnectingCall(boolean z) {
        this.mAutoProgressConnecting = z;
    }

    public void setNextDialFailImmediately(boolean z) {
        this.mNextDialFailImmediately = z;
    }

    public boolean switchActiveAndHeldOrWaiting() {
        CallInfo callInfo;
        boolean z;
        int i = 0;
        for (CallInfo callInfo2 : this.mCalls) {
            if (callInfo2 != null && callInfo2.mState == State.HOLDING) {
                z = true;
                break;
            }
        }
        z = false;
        while (i < this.mCalls.length) {
            callInfo2 = this.mCalls[i];
            if (callInfo2 != null) {
                if (callInfo2.mState == State.ACTIVE) {
                    callInfo2.mState = State.HOLDING;
                } else if (callInfo2.mState == State.HOLDING) {
                    callInfo2.mState = State.ACTIVE;
                } else if (!z && callInfo2.isRinging()) {
                    callInfo2.mState = State.ACTIVE;
                }
            }
            i++;
        }
        return true;
    }

    public boolean triggerHangupAll() {
        boolean z;
        synchronized (this) {
            z = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo callInfo = this.mCalls[i];
                if (this.mCalls[i] != null) {
                    z = true;
                }
                this.mCalls[i] = null;
            }
        }
        return z;
    }

    public boolean triggerHangupBackground() {
        boolean z;
        synchronized (this) {
            z = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo callInfo = this.mCalls[i];
                if (callInfo != null && callInfo.mState == State.HOLDING) {
                    this.mCalls[i] = null;
                    z = true;
                }
            }
        }
        return z;
    }

    public boolean triggerHangupForeground() {
        boolean z;
        int i = 0;
        synchronized (this) {
            z = false;
            for (int i2 = 0; i2 < this.mCalls.length; i2++) {
                CallInfo callInfo = this.mCalls[i2];
                if (callInfo != null && (callInfo.mState == State.INCOMING || callInfo.mState == State.WAITING)) {
                    this.mCalls[i2] = null;
                    z = true;
                }
            }
            while (i < this.mCalls.length) {
                CallInfo callInfo2 = this.mCalls[i];
                if (callInfo2 != null && (callInfo2.mState == State.DIALING || callInfo2.mState == State.ACTIVE || callInfo2.mState == State.ALERTING)) {
                    this.mCalls[i] = null;
                    z = true;
                }
                i++;
            }
        }
        return z;
    }

    /* JADX WARNING: Missing block: B:41:?, code skipped:
            return true;
     */
    public boolean triggerRing(java.lang.String r9) {
        /*
        r8 = this;
        r3 = 1;
        r4 = 0;
        monitor-enter(r8);
        r0 = -1;
        r2 = r4;
        r1 = r4;
    L_0x0006:
        r5 = r8.mCalls;	 Catch:{ all -> 0x003b }
        r5 = r5.length;	 Catch:{ all -> 0x003b }
        if (r1 >= r5) goto L_0x002f;
    L_0x000b:
        r5 = r8.mCalls;	 Catch:{ all -> 0x003b }
        r5 = r5[r1];	 Catch:{ all -> 0x003b }
        if (r5 != 0) goto L_0x0017;
    L_0x0011:
        if (r0 >= 0) goto L_0x0017;
    L_0x0013:
        r0 = r1;
    L_0x0014:
        r1 = r1 + 1;
        goto L_0x0006;
    L_0x0017:
        if (r5 == 0) goto L_0x0056;
    L_0x0019:
        r6 = r5.mState;	 Catch:{ all -> 0x003b }
        r7 = com.android.internal.telephony.test.CallInfo.State.INCOMING;	 Catch:{ all -> 0x003b }
        if (r6 == r7) goto L_0x0025;
    L_0x001f:
        r6 = r5.mState;	 Catch:{ all -> 0x003b }
        r7 = com.android.internal.telephony.test.CallInfo.State.WAITING;	 Catch:{ all -> 0x003b }
        if (r6 != r7) goto L_0x0056;
    L_0x0025:
        r0 = "ModelInterpreter";
        r1 = "triggerRing failed; phone already ringing";
        android.telephony.Rlog.w(r0, r1);	 Catch:{ all -> 0x003b }
        monitor-exit(r8);	 Catch:{ all -> 0x003b }
        r3 = r4;
    L_0x002e:
        return r3;
    L_0x002f:
        if (r0 >= 0) goto L_0x003e;
    L_0x0031:
        r0 = "ModelInterpreter";
        r1 = "triggerRing failed; all full";
        android.telephony.Rlog.w(r0, r1);	 Catch:{ all -> 0x003b }
        monitor-exit(r8);	 Catch:{ all -> 0x003b }
        r3 = r4;
        goto L_0x002e;
    L_0x003b:
        r0 = move-exception;
        monitor-exit(r8);	 Catch:{ all -> 0x003b }
        throw r0;
    L_0x003e:
        r1 = r8.mCalls;	 Catch:{ all -> 0x003b }
        r4 = android.telephony.PhoneNumberUtils.extractNetworkPortion(r9);	 Catch:{ all -> 0x003b }
        r4 = com.android.internal.telephony.test.CallInfo.createIncomingCall(r4);	 Catch:{ all -> 0x003b }
        r1[r0] = r4;	 Catch:{ all -> 0x003b }
        if (r2 == 0) goto L_0x0054;
    L_0x004c:
        r1 = r8.mCalls;	 Catch:{ all -> 0x003b }
        r0 = r1[r0];	 Catch:{ all -> 0x003b }
        r1 = com.android.internal.telephony.test.CallInfo.State.WAITING;	 Catch:{ all -> 0x003b }
        r0.mState = r1;	 Catch:{ all -> 0x003b }
    L_0x0054:
        monitor-exit(r8);	 Catch:{ all -> 0x003b }
        goto L_0x002e;
    L_0x0056:
        if (r5 == 0) goto L_0x0014;
    L_0x0058:
        r2 = r3;
        goto L_0x0014;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.test.SimulatedGsmCallState.triggerRing(java.lang.String):boolean");
    }
}
