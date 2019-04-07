package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.Rlog;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

public abstract class WakeLockStateMachine extends StateMachine {
    protected static final boolean DBG = true;
    protected static final int EVENT_BROADCAST_COMPLETE = 2;
    public static final int EVENT_NEW_SMS_MESSAGE = 1;
    static final int EVENT_RELEASE_WAKE_LOCK = 3;
    static final int EVENT_UPDATE_PHONE_OBJECT = 4;
    private static final int WAKE_LOCK_TIMEOUT = 3000;
    protected Context mContext;
    private final DefaultState mDefaultState = new DefaultState();
    private final IdleState mIdleState = new IdleState();
    protected PhoneBase mPhone;
    protected final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            WakeLockStateMachine.this.sendMessage(2);
        }
    };
    private final WaitingState mWaitingState = new WaitingState();
    private final WakeLock mWakeLock;

    class DefaultState extends State {
        DefaultState() {
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 4:
                    WakeLockStateMachine.this.mPhone = (PhoneBase) message.obj;
                    WakeLockStateMachine.this.log("updatePhoneObject: phone=" + WakeLockStateMachine.this.mPhone.getClass().getSimpleName());
                    break;
                default:
                    String str = "processMessage: unhandled message type " + message.what;
                    if (!Build.IS_DEBUGGABLE) {
                        WakeLockStateMachine.this.loge(str);
                        break;
                    }
                    throw new RuntimeException(str);
            }
            return true;
        }
    }

    class IdleState extends State {
        IdleState() {
        }

        public void enter() {
            WakeLockStateMachine.this.sendMessageDelayed(3, 3000);
        }

        public void exit() {
            WakeLockStateMachine.this.mWakeLock.acquire();
            WakeLockStateMachine.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 1:
                    if (!WakeLockStateMachine.this.handleSmsMessage(message)) {
                        return true;
                    }
                    WakeLockStateMachine.this.transitionTo(WakeLockStateMachine.this.mWaitingState);
                    return true;
                case 3:
                    WakeLockStateMachine.this.mWakeLock.release();
                    if (WakeLockStateMachine.this.mWakeLock.isHeld()) {
                        WakeLockStateMachine.this.log("mWakeLock is still held after release");
                        return true;
                    }
                    WakeLockStateMachine.this.log("mWakeLock released");
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
            switch (message.what) {
                case 1:
                    WakeLockStateMachine.this.log("deferring message until return to idle");
                    WakeLockStateMachine.this.deferMessage(message);
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

    protected WakeLockStateMachine(String str, Context context, PhoneBase phoneBase) {
        super(str);
        this.mContext = context;
        this.mPhone = phoneBase;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, str);
        this.mWakeLock.acquire();
        addState(this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mWaitingState, this.mDefaultState);
        setInitialState(this.mIdleState);
    }

    public final void dispatchSmsMessage(Object obj) {
        sendMessage(1, obj);
    }

    public final void dispose() {
        quit();
    }

    public abstract boolean handleSmsMessage(Message message);

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

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public void updatePhoneObject(PhoneBase phoneBase) {
        sendMessage(4, phoneBase);
    }
}
