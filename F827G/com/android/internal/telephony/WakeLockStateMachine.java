package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.Rlog;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class WakeLockStateMachine extends StateMachine {
    protected static final boolean DBG = true;
    protected static final int EVENT_BROADCAST_COMPLETE = 2;
    public static final int EVENT_NEW_SMS_MESSAGE = 1;
    static final int EVENT_RELEASE_WAKE_LOCK = 3;
    static final int EVENT_UPDATE_PHONE_OBJECT = 4;
    private static final int WAKE_LOCK_TIMEOUT = 3000;
    protected Context mContext;
    protected PhoneBase mPhone;
    private final PowerManager.WakeLock mWakeLock;
    private final DefaultState mDefaultState = new DefaultState();
    private final IdleState mIdleState = new IdleState();
    private final WaitingState mWaitingState = new WaitingState();
    protected final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.WakeLockStateMachine.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            WakeLockStateMachine.this.sendMessage(2);
        }
    };

    protected abstract boolean handleSmsMessage(Message message);

    /* JADX INFO: Access modifiers changed from: protected */
    public WakeLockStateMachine(String debugTag, Context context, PhoneBase phone) {
        super(debugTag);
        this.mContext = context;
        this.mPhone = phone;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, debugTag);
        this.mWakeLock.acquire();
        addState(this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mWaitingState, this.mDefaultState);
        setInitialState(this.mIdleState);
    }

    public void updatePhoneObject(PhoneBase phone) {
        sendMessage(4, phone);
    }

    public final void dispose() {
        quit();
    }

    protected void onQuitting() {
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public final void dispatchSmsMessage(Object obj) {
        sendMessage(1, obj);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    class DefaultState extends State {
        DefaultState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 4:
                    WakeLockStateMachine.this.mPhone = (PhoneBase) msg.obj;
                    WakeLockStateMachine.this.log("updatePhoneObject: phone=" + WakeLockStateMachine.this.mPhone.getClass().getSimpleName());
                    return true;
                default:
                    String errorText = "processMessage: unhandled message type " + msg.what;
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException(errorText);
                    }
                    WakeLockStateMachine.this.loge(errorText);
                    return true;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    class IdleState extends State {
        IdleState() {
        }

        public void enter() {
            WakeLockStateMachine.this.sendMessageDelayed(3, 3000L);
        }

        public void exit() {
            WakeLockStateMachine.this.mWakeLock.acquire();
            WakeLockStateMachine.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (!WakeLockStateMachine.this.handleSmsMessage(msg)) {
                        return true;
                    }
                    WakeLockStateMachine.this.transitionTo(WakeLockStateMachine.this.mWaitingState);
                    return true;
                case 2:
                default:
                    return false;
                case 3:
                    WakeLockStateMachine.this.mWakeLock.release();
                    if (WakeLockStateMachine.this.mWakeLock.isHeld()) {
                        WakeLockStateMachine.this.log("mWakeLock is still held after release");
                        return true;
                    }
                    WakeLockStateMachine.this.log("mWakeLock released");
                    return true;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    class WaitingState extends State {
        WaitingState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    WakeLockStateMachine.this.log("deferring message until return to idle");
                    WakeLockStateMachine.this.deferMessage(msg);
                    return true;
                case 2:
                    WakeLockStateMachine.this.log("broadcast complete, returning to idle");
                    WakeLockStateMachine.this.transitionTo(WakeLockStateMachine.this.mIdleState);
                    return true;
                case 3:
                    WakeLockStateMachine.this.mWakeLock.release();
                    if (WakeLockStateMachine.this.mWakeLock.isHeld()) {
                        return true;
                    }
                    WakeLockStateMachine.this.loge("mWakeLock released while still in WaitingState!");
                    return true;
                default:
                    return false;
            }
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
}
