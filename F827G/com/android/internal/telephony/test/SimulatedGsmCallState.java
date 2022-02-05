package com.android.internal.telephony.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.test.CallInfo;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class SimulatedGsmCallState extends Handler {
    static final int CONNECTING_PAUSE_MSEC = 500;
    static final int EVENT_PROGRESS_CALL_STATE = 1;
    static final int MAX_CALLS = 7;
    private boolean mNextDialFailImmediately;
    CallInfo[] mCalls = new CallInfo[7];
    private boolean mAutoProgressConnecting = true;

    public SimulatedGsmCallState(Looper looper) {
        super(looper);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        synchronized (this) {
            switch (msg.what) {
                case 1:
                    progressConnectingCallState();
                    break;
            }
        }
    }

    public boolean triggerRing(String number) {
        synchronized (this) {
            int empty = -1;
            boolean isCallWaiting = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call == null && empty < 0) {
                    empty = i;
                } else if (call != null && (call.mState == CallInfo.State.INCOMING || call.mState == CallInfo.State.WAITING)) {
                    Rlog.w("ModelInterpreter", "triggerRing failed; phone already ringing");
                    return false;
                } else if (call != null) {
                    isCallWaiting = true;
                }
            }
            if (empty < 0) {
                Rlog.w("ModelInterpreter", "triggerRing failed; all full");
                return false;
            }
            this.mCalls[empty] = CallInfo.createIncomingCall(PhoneNumberUtils.extractNetworkPortion(number));
            if (isCallWaiting) {
                this.mCalls[empty].mState = CallInfo.State.WAITING;
            }
            return true;
        }
    }

    public void progressConnectingCallState() {
        synchronized (this) {
            int i = 0;
            while (true) {
                if (i >= this.mCalls.length) {
                    break;
                }
                CallInfo call = this.mCalls[i];
                if (call == null || call.mState != CallInfo.State.DIALING) {
                    if (call != null && call.mState == CallInfo.State.ALERTING) {
                        call.mState = CallInfo.State.ACTIVE;
                        break;
                    }
                    i++;
                } else {
                    call.mState = CallInfo.State.ALERTING;
                    if (this.mAutoProgressConnecting) {
                        sendMessageDelayed(obtainMessage(1, call), 500L);
                    }
                }
            }
        }
    }

    public void progressConnectingToActive() {
        synchronized (this) {
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && (call.mState == CallInfo.State.DIALING || call.mState == CallInfo.State.ALERTING)) {
                    call.mState = CallInfo.State.ACTIVE;
                    break;
                }
            }
        }
    }

    public void setAutoProgressConnectingCall(boolean b) {
        this.mAutoProgressConnecting = b;
    }

    public void setNextDialFailImmediately(boolean b) {
        this.mNextDialFailImmediately = b;
    }

    public boolean triggerHangupForeground() {
        boolean found;
        synchronized (this) {
            found = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && (call.mState == CallInfo.State.INCOMING || call.mState == CallInfo.State.WAITING)) {
                    this.mCalls[i] = null;
                    found = true;
                }
            }
            for (int i2 = 0; i2 < this.mCalls.length; i2++) {
                CallInfo call2 = this.mCalls[i2];
                if (call2 != null && (call2.mState == CallInfo.State.DIALING || call2.mState == CallInfo.State.ACTIVE || call2.mState == CallInfo.State.ALERTING)) {
                    this.mCalls[i2] = null;
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean triggerHangupBackground() {
        boolean found;
        synchronized (this) {
            found = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && call.mState == CallInfo.State.HOLDING) {
                    this.mCalls[i] = null;
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean triggerHangupAll() {
        boolean found;
        synchronized (this) {
            found = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo callInfo = this.mCalls[i];
                if (this.mCalls[i] != null) {
                    found = true;
                }
                this.mCalls[i] = null;
            }
        }
        return found;
    }

    public boolean onAnswer() {
        synchronized (this) {
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && (call.mState == CallInfo.State.INCOMING || call.mState == CallInfo.State.WAITING)) {
                    return switchActiveAndHeldOrWaiting();
                }
            }
            return false;
        }
    }

    public boolean onHangup() {
        boolean found = false;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo call = this.mCalls[i];
            if (!(call == null || call.mState == CallInfo.State.WAITING)) {
                this.mCalls[i] = null;
                found = true;
            }
        }
        return found;
    }

    public boolean onChld(char c0, char c1) {
        int callIndex = 0;
        if (c1 != 0 && (c1 - '1' < 0 || callIndex >= this.mCalls.length)) {
            return false;
        }
        switch (c0) {
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /* 48 */:
                return releaseHeldOrUDUB();
            case '1':
                if (c1 <= 0) {
                    return releaseActiveAcceptHeldOrWaiting();
                }
                if (this.mCalls[callIndex] == null) {
                    return false;
                }
                this.mCalls[callIndex] = null;
                return true;
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /* 50 */:
                if (c1 <= 0) {
                    return switchActiveAndHeldOrWaiting();
                }
                return separateCall(callIndex);
            case '3':
                return conference();
            case '4':
                return explicitCallTransfer();
            case '5':
                return false;
            default:
                return false;
        }
    }

    public boolean releaseHeldOrUDUB() {
        boolean found = false;
        int i = 0;
        while (true) {
            if (i >= this.mCalls.length) {
                break;
            }
            CallInfo c = this.mCalls[i];
            if (c != null && c.isRinging()) {
                found = true;
                this.mCalls[i] = null;
                break;
            }
            i++;
        }
        if (found) {
            return true;
        }
        for (int i2 = 0; i2 < this.mCalls.length; i2++) {
            CallInfo c2 = this.mCalls[i2];
            if (c2 != null && c2.mState == CallInfo.State.HOLDING) {
                this.mCalls[i2] = null;
            }
        }
        return true;
    }

    public boolean releaseActiveAcceptHeldOrWaiting() {
        boolean foundHeld = false;
        boolean foundActive = false;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null && c.mState == CallInfo.State.ACTIVE) {
                this.mCalls[i] = null;
                foundActive = true;
            }
        }
        if (!foundActive) {
            for (int i2 = 0; i2 < this.mCalls.length; i2++) {
                CallInfo c2 = this.mCalls[i2];
                if (c2 != null && (c2.mState == CallInfo.State.DIALING || c2.mState == CallInfo.State.ALERTING)) {
                    this.mCalls[i2] = null;
                }
            }
        }
        for (int i3 = 0; i3 < this.mCalls.length; i3++) {
            CallInfo c3 = this.mCalls[i3];
            if (c3 != null && c3.mState == CallInfo.State.HOLDING) {
                c3.mState = CallInfo.State.ACTIVE;
                foundHeld = true;
            }
        }
        if (!foundHeld) {
            int i4 = 0;
            while (true) {
                if (i4 >= this.mCalls.length) {
                    break;
                }
                CallInfo c4 = this.mCalls[i4];
                if (c4 != null && c4.isRinging()) {
                    c4.mState = CallInfo.State.ACTIVE;
                    break;
                }
                i4++;
            }
        }
        return true;
    }

    public boolean switchActiveAndHeldOrWaiting() {
        boolean hasHeld = false;
        int i = 0;
        while (true) {
            if (i >= this.mCalls.length) {
                break;
            }
            CallInfo c = this.mCalls[i];
            if (c != null && c.mState == CallInfo.State.HOLDING) {
                hasHeld = true;
                break;
            }
            i++;
        }
        for (int i2 = 0; i2 < this.mCalls.length; i2++) {
            CallInfo c2 = this.mCalls[i2];
            if (c2 != null) {
                if (c2.mState == CallInfo.State.ACTIVE) {
                    c2.mState = CallInfo.State.HOLDING;
                } else if (c2.mState == CallInfo.State.HOLDING) {
                    c2.mState = CallInfo.State.ACTIVE;
                } else if (!hasHeld && c2.isRinging()) {
                    c2.mState = CallInfo.State.ACTIVE;
                }
            }
        }
        return true;
    }

    public boolean separateCall(int index) {
        CallInfo cb;
        try {
            CallInfo c = this.mCalls[index];
            if (c == null || c.isConnecting() || countActiveLines() != 1) {
                return false;
            }
            c.mState = CallInfo.State.ACTIVE;
            c.mIsMpty = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                int countHeld = 0;
                int lastHeld = 0;
                if (!(i == index || (cb = this.mCalls[i]) == null || cb.mState != CallInfo.State.ACTIVE)) {
                    cb.mState = CallInfo.State.HOLDING;
                    countHeld = 0 + 1;
                    lastHeld = i;
                }
                if (countHeld == 1) {
                    this.mCalls[lastHeld].mIsMpty = false;
                }
            }
            return true;
        } catch (InvalidStateEx e) {
            return false;
        }
    }

    public boolean conference() {
        int countCalls = 0;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                countCalls++;
                if (c.isConnecting()) {
                    return false;
                }
            }
        }
        for (int i2 = 0; i2 < this.mCalls.length; i2++) {
            CallInfo c2 = this.mCalls[i2];
            if (c2 != null) {
                c2.mState = CallInfo.State.ACTIVE;
                if (countCalls > 0) {
                    c2.mIsMpty = true;
                }
            }
        }
        return true;
    }

    public boolean explicitCallTransfer() {
        int countCalls = 0;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                countCalls++;
                if (c.isConnecting()) {
                    return false;
                }
            }
        }
        return triggerHangupAll();
    }

    public boolean onDial(String address) {
        int freeSlot = -1;
        Rlog.d("GSM", "SC> dial '" + address + "'");
        if (this.mNextDialFailImmediately) {
            this.mNextDialFailImmediately = false;
            Rlog.d("GSM", "SC< dial fail (per request)");
            return false;
        }
        String phNum = PhoneNumberUtils.extractNetworkPortion(address);
        if (phNum.length() == 0) {
            Rlog.d("GSM", "SC< dial fail (invalid ph num)");
            return false;
        } else if (!phNum.startsWith("*99") || !phNum.endsWith("#")) {
            try {
                if (countActiveLines() > 1) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                for (int i = 0; i < this.mCalls.length; i++) {
                    if (freeSlot < 0 && this.mCalls[i] == null) {
                        freeSlot = i;
                    }
                    if (this.mCalls[i] == null || this.mCalls[i].isActiveOrHeld()) {
                        if (this.mCalls[i] != null && this.mCalls[i].mState == CallInfo.State.ACTIVE) {
                            this.mCalls[i].mState = CallInfo.State.HOLDING;
                        }
                    } else {
                        Rlog.d("GSM", "SC< dial fail (invalid call state)");
                        return false;
                    }
                }
                if (freeSlot < 0) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                this.mCalls[freeSlot] = CallInfo.createOutgoingCall(phNum);
                if (this.mAutoProgressConnecting) {
                    sendMessageDelayed(obtainMessage(1, this.mCalls[freeSlot]), 500L);
                }
                Rlog.d("GSM", "SC< dial (slot = " + freeSlot + ")");
                return true;
            } catch (InvalidStateEx e) {
                Rlog.d("GSM", "SC< dial fail (invalid call state)");
                return false;
            }
        } else {
            Rlog.d("GSM", "SC< dial ignored (gprs)");
            return true;
        }
    }

    public List<DriverCall> getDriverCalls() {
        ArrayList<DriverCall> ret = new ArrayList<>(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                ret.add(c.toDriverCall(i + 1));
            }
        }
        Rlog.d("GSM", "SC< getDriverCalls " + ret);
        return ret;
    }

    public List<String> getClccLines() {
        ArrayList<String> ret = new ArrayList<>(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                ret.add(c.toCLCCLine(i + 1));
            }
        }
        return ret;
    }

    private int countActiveLines() throws InvalidStateEx {
        boolean hasMpty = false;
        boolean hasHeld = false;
        boolean hasActive = false;
        boolean hasConnecting = false;
        boolean hasRinging = false;
        boolean mptyIsHeld = false;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo call = this.mCalls[i];
            if (call != null) {
                if (!hasMpty && call.mIsMpty) {
                    mptyIsHeld = call.mState == CallInfo.State.HOLDING;
                } else if (call.mIsMpty && mptyIsHeld && call.mState == CallInfo.State.ACTIVE) {
                    Rlog.e("ModelInterpreter", "Invalid state");
                    throw new InvalidStateEx();
                } else if (!call.mIsMpty && hasMpty && mptyIsHeld && call.mState == CallInfo.State.HOLDING) {
                    Rlog.e("ModelInterpreter", "Invalid state");
                    throw new InvalidStateEx();
                }
                hasMpty |= call.mIsMpty;
                hasHeld |= call.mState == CallInfo.State.HOLDING;
                hasActive |= call.mState == CallInfo.State.ACTIVE;
                hasConnecting |= call.isConnecting();
                hasRinging |= call.isRinging();
            }
        }
        int ret = 0;
        if (hasHeld) {
            ret = 0 + 1;
        }
        if (hasActive) {
            ret++;
        }
        if (hasConnecting) {
            ret++;
        }
        return hasRinging ? ret + 1 : ret;
    }
}
