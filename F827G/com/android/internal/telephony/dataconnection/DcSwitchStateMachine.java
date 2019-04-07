package com.android.internal.telephony.dataconnection;

import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel.RequestInfo;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

public class DcSwitchStateMachine extends StateMachine {
    private static final int BASE = 274432;
    private static final boolean DBG = true;
    private static final int EVENT_CONNECTED = 274432;
    private static final String LOG_TAG = "DcSwitchSM";
    private static final boolean VDBG = false;
    private AsyncChannel mAc;
    private AttachedState mAttachedState = new AttachedState();
    private AttachingState mAttachingState = new AttachingState();
    private DefaultState mDefaultState = new DefaultState();
    private DetachingState mDetachingState = new DetachingState();
    private int mId;
    private IdleState mIdleState = new IdleState();
    private Phone mPhone;

    private class AttachedState extends State {
        private AttachedState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("AttachedState: enter");
            DctController.getInstance().executeAllRequests(DcSwitchStateMachine.this.mId);
        }

        public boolean processMessage(Message message) {
            RequestInfo requestInfo;
            switch (message.what) {
                case 278528:
                    requestInfo = (RequestInfo) message.obj;
                    DcSwitchStateMachine.this.log("AttachedState: REQ_CONNECT, apnRequest=" + requestInfo);
                    DctController.getInstance().executeRequest(requestInfo);
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278529, 1);
                    return true;
                case 278530:
                    requestInfo = (RequestInfo) message.obj;
                    DcSwitchStateMachine.this.log("AttachedState: REQ_DISCONNECT apnRequest=" + requestInfo);
                    DctController.getInstance().releaseRequest(requestInfo);
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278529, 1);
                    return true;
                case 278532:
                    DcSwitchStateMachine.this.log("AttachedState: REQ_DISCONNECT_ALL");
                    DctController.getInstance().releaseAllRequests(DcSwitchStateMachine.this.mId);
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278533, 1);
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mDetachingState);
                    return true;
                case 278539:
                    DcSwitchStateMachine.this.log("AttachedState: EVENT_DATA_DETACHED");
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mAttachingState);
                    return true;
                default:
                    return false;
            }
        }
    }

    private class AttachingState extends State {
        private AttachingState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("AttachingState: enter");
            if (DcSwitchStateMachine.this.mPhone.getServiceState() != null && DcSwitchStateMachine.this.mPhone.getServiceState().getDataRegState() == 0) {
                DcSwitchStateMachine.this.log("AttachingState: Data already registered. Move to Attached");
                DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mAttachedState);
            }
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 278528:
                    DcSwitchStateMachine.this.log("AttachingState: REQ_CONNECT");
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278529, 1);
                    return true;
                case 278532:
                    DcSwitchStateMachine.this.log("AttachingState: REQ_DISCONNECT_ALL");
                    DctController.getInstance().releaseAllRequests(DcSwitchStateMachine.this.mId);
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278533, 1);
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mDetachingState);
                    return true;
                case 278538:
                    DcSwitchStateMachine.this.log("AttachingState: EVENT_DATA_ATTACHED");
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mAttachedState);
                    return true;
                default:
                    return false;
            }
        }
    }

    private class DefaultState extends State {
        private DefaultState() {
        }

        public boolean processMessage(Message message) {
            int i = 0;
            AsyncChannel access$700;
            switch (message.what) {
                case 69633:
                    if (DcSwitchStateMachine.this.mAc == null) {
                        DcSwitchStateMachine.this.mAc = new AsyncChannel();
                        DcSwitchStateMachine.this.mAc.connected(null, DcSwitchStateMachine.this.getHandler(), message.replyTo);
                        DcSwitchStateMachine.this.mAc.replyToMessage(message, 69634, 0, DcSwitchStateMachine.this.mId, "hi");
                        break;
                    }
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 69634, 3);
                    break;
                case 69635:
                    DcSwitchStateMachine.this.mAc.disconnect();
                    break;
                case 69636:
                    DcSwitchStateMachine.this.mAc = null;
                    break;
                case 278534:
                    boolean z = DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mIdleState;
                    access$700 = DcSwitchStateMachine.this.mAc;
                    if (z) {
                        i = 1;
                    }
                    access$700.replyToMessage(message, 278535, i);
                    break;
                case 278536:
                    int i2 = (DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mIdleState || DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mDetachingState) ? true : 0;
                    access$700 = DcSwitchStateMachine.this.mAc;
                    if (i2 != 0) {
                        i = 1;
                    }
                    access$700.replyToMessage(message, 278537, i);
                    break;
                default:
                    DcSwitchStateMachine.this.log("DefaultState: shouldn't happen but ignore msg.what=0x" + Integer.toHexString(message.what));
                    break;
            }
            return true;
        }
    }

    private class DetachingState extends State {
        private DetachingState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("DetachingState: enter");
            DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mIdleState);
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 278532:
                    DcSwitchStateMachine.this.log("DetachingState: REQ_DISCONNECT_ALL, already detaching");
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278533, 1);
                    return true;
                case 278539:
                    DcSwitchStateMachine.this.log("DetachingState: EVENT_DATA_DETACHED");
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mIdleState);
                    return true;
                default:
                    return false;
            }
        }
    }

    private class IdleState extends State {
        private IdleState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("IdleState: enter");
            try {
                DctController.getInstance().processRequests();
            } catch (RuntimeException e) {
                DcSwitchStateMachine.this.loge("DctController is not ready");
            }
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 274432:
                    DcSwitchStateMachine.this.log("IdleState: Receive invalid event EVENT_CONNECTED!");
                    return true;
                case 278528:
                    DcSwitchStateMachine.this.log("IdleState: REQ_CONNECT");
                    PhoneBase phoneBase = (PhoneBase) ((PhoneProxy) DcSwitchStateMachine.this.mPhone).getActivePhone();
                    boolean z = SystemProperties.getBoolean("persist.radio.primarycard", false);
                    int subId = phoneBase.getSubId();
                    DcSwitchStateMachine.this.log("Setting default DDS on " + subId + " primary Sub feature" + z);
                    if (!z) {
                        SubscriptionController.getInstance().setDefaultDataSubId(subId);
                    }
                    DcSwitchStateMachine.this.mAc.replyToMessage(message, 278529, 1);
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mAttachingState);
                    return true;
                case 278538:
                    DcSwitchStateMachine.this.log("AttachingState: EVENT_DATA_ATTACHED");
                    DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mAttachedState);
                    return true;
                default:
                    return false;
            }
        }
    }

    protected DcSwitchStateMachine(Phone phone, String str, int i) {
        super(str);
        log("DcSwitchState constructor E");
        this.mPhone = phone;
        this.mId = i;
        addState(this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mAttachingState, this.mDefaultState);
        addState(this.mAttachedState, this.mDefaultState);
        addState(this.mDetachingState, this.mDefaultState);
        setInitialState(this.mIdleState);
        log("DcSwitchState constructor X");
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[" + getName() + "] " + str);
    }
}
