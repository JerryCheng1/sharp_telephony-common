package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DcSwitchStateMachine extends StateMachine {
    private static final int BASE = 274432;
    private static final boolean DBG = true;
    private static final int EVENT_CONNECTED = 274432;
    private static final String LOG_TAG = "DcSwitchSM";
    private static final boolean VDBG = false;
    private AsyncChannel mAc;
    private int mId;
    private Phone mPhone;
    private IdleState mIdleState = new IdleState();
    private AttachingState mAttachingState = new AttachingState();
    private AttachedState mAttachedState = new AttachedState();
    private DetachingState mDetachingState = new DetachingState();
    private DefaultState mDefaultState = new DefaultState();

    /* JADX INFO: Access modifiers changed from: protected */
    public DcSwitchStateMachine(Phone phone, String name, int id) {
        super(name);
        log("DcSwitchState constructor E");
        this.mPhone = phone;
        this.mId = id;
        addState(this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mAttachingState, this.mDefaultState);
        addState(this.mAttachedState, this.mDefaultState);
        addState(this.mDetachingState, this.mDefaultState);
        setInitialState(this.mIdleState);
        log("DcSwitchState constructor X");
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 274432:
                    DcSwitchStateMachine.this.log("IdleState: Receive invalid event EVENT_CONNECTED!");
                    return true;
                case 278528:
                    DcSwitchStateMachine.this.log("IdleState: REQ_CONNECT");
                    boolean isPrimarySubFeatureEnable = SystemProperties.getBoolean("persist.radio.primarycard", false);
                    int subId = ((PhoneBase) ((PhoneProxy) DcSwitchStateMachine.this.mPhone).getActivePhone()).getSubId();
                    DcSwitchStateMachine.this.log("Setting default DDS on " + subId + " primary Sub feature" + isPrimarySubFeatureEnable);
                    if (!isPrimarySubFeatureEnable) {
                        SubscriptionController.getInstance().setDefaultDataSubId(subId);
                    }
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278529, 1);
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 278528:
                    DcSwitchStateMachine.this.log("AttachingState: REQ_CONNECT");
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278529, 1);
                    return true;
                case 278532:
                    DcSwitchStateMachine.this.log("AttachingState: REQ_DISCONNECT_ALL");
                    DctController.getInstance().releaseAllRequests(DcSwitchStateMachine.this.mId);
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278533, 1);
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class AttachedState extends State {
        private AttachedState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("AttachedState: enter");
            DctController.getInstance().executeAllRequests(DcSwitchStateMachine.this.mId);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 278528:
                    DcSwitchAsyncChannel.RequestInfo apnRequest = (DcSwitchAsyncChannel.RequestInfo) msg.obj;
                    DcSwitchStateMachine.this.log("AttachedState: REQ_CONNECT, apnRequest=" + apnRequest);
                    DctController.getInstance().executeRequest(apnRequest);
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278529, 1);
                    return true;
                case 278530:
                    DcSwitchAsyncChannel.RequestInfo apnRequest2 = (DcSwitchAsyncChannel.RequestInfo) msg.obj;
                    DcSwitchStateMachine.this.log("AttachedState: REQ_DISCONNECT apnRequest=" + apnRequest2);
                    DctController.getInstance().releaseRequest(apnRequest2);
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278529, 1);
                    return true;
                case 278532:
                    DcSwitchStateMachine.this.log("AttachedState: REQ_DISCONNECT_ALL");
                    DctController.getInstance().releaseAllRequests(DcSwitchStateMachine.this.mId);
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278533, 1);
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class DetachingState extends State {
        private DetachingState() {
        }

        public void enter() {
            DcSwitchStateMachine.this.log("DetachingState: enter");
            DcSwitchStateMachine.this.transitionTo(DcSwitchStateMachine.this.mIdleState);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 278532:
                    DcSwitchStateMachine.this.log("DetachingState: REQ_DISCONNECT_ALL, already detaching");
                    DcSwitchStateMachine.this.mAc.replyToMessage(msg, 278533, 1);
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class DefaultState extends State {
        private DefaultState() {
        }

        public boolean processMessage(Message msg) {
            int i = 0;
            switch (msg.what) {
                case 69633:
                    if (DcSwitchStateMachine.this.mAc == null) {
                        DcSwitchStateMachine.this.mAc = new AsyncChannel();
                        DcSwitchStateMachine.this.mAc.connected((Context) null, DcSwitchStateMachine.this.getHandler(), msg.replyTo);
                        DcSwitchStateMachine.this.mAc.replyToMessage(msg, 69634, 0, DcSwitchStateMachine.this.mId, "hi");
                        break;
                    } else {
                        DcSwitchStateMachine.this.mAc.replyToMessage(msg, 69634, 3);
                        break;
                    }
                case 69635:
                    DcSwitchStateMachine.this.mAc.disconnect();
                    break;
                case 69636:
                    DcSwitchStateMachine.this.mAc = null;
                    break;
                case 278534:
                    boolean val = DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mIdleState;
                    AsyncChannel asyncChannel = DcSwitchStateMachine.this.mAc;
                    if (val) {
                        i = 1;
                    }
                    asyncChannel.replyToMessage(msg, 278535, i);
                    break;
                case 278536:
                    boolean val2 = DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mIdleState || DcSwitchStateMachine.this.getCurrentState() == DcSwitchStateMachine.this.mDetachingState;
                    AsyncChannel asyncChannel2 = DcSwitchStateMachine.this.mAc;
                    if (val2) {
                        i = 1;
                    }
                    asyncChannel2.replyToMessage(msg, 278537, i);
                    break;
                default:
                    DcSwitchStateMachine.this.log("DefaultState: shouldn't happen but ignore msg.what=0x" + Integer.toHexString(msg.what));
                    break;
            }
            return true;
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + getName() + "] " + s);
    }
}
