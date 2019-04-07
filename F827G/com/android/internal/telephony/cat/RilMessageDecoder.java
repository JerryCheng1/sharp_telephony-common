package com.android.internal.telephony.cat;

import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

class RilMessageDecoder extends StateMachine {
    private static final int CMD_PARAMS_READY = 2;
    private static final int CMD_START = 1;
    private static RilMessageDecoder[] mInstance = null;
    private static int mSimCount = 0;
    private Handler mCaller = null;
    private CommandParamsFactory mCmdParamsFactory = null;
    private RilMessage mCurrentRilMessage = null;
    private StateCmdParamsReady mStateCmdParamsReady = new StateCmdParamsReady();
    private StateStart mStateStart = new StateStart();

    private class StateCmdParamsReady extends State {
        private StateCmdParamsReady() {
        }

        public boolean processMessage(Message message) {
            if (message.what == 2) {
                RilMessageDecoder.this.mCurrentRilMessage.mResCode = ResultCode.fromInt(message.arg1);
                RilMessageDecoder.this.mCurrentRilMessage.mData = message.obj;
                RilMessageDecoder.this.sendCmdForExecution(RilMessageDecoder.this.mCurrentRilMessage);
                RilMessageDecoder.this.transitionTo(RilMessageDecoder.this.mStateStart);
            } else {
                CatLog.d((Object) this, "StateCmdParamsReady expecting CMD_PARAMS_READY=2 got " + message.what);
                RilMessageDecoder.this.deferMessage(message);
            }
            return true;
        }
    }

    private class StateStart extends State {
        private StateStart() {
        }

        public boolean processMessage(Message message) {
            if (message.what != 1) {
                CatLog.d((Object) this, "StateStart unexpected expecting START=1 got " + message.what);
            } else if (RilMessageDecoder.this.decodeMessageParams((RilMessage) message.obj)) {
                RilMessageDecoder.this.transitionTo(RilMessageDecoder.this.mStateCmdParamsReady);
            }
            return true;
        }
    }

    private RilMessageDecoder() {
        super("RilMessageDecoder");
    }

    private RilMessageDecoder(Handler handler, IccFileHandler iccFileHandler) {
        super("RilMessageDecoder");
        addState(this.mStateStart);
        addState(this.mStateCmdParamsReady);
        setInitialState(this.mStateStart);
        this.mCaller = handler;
        this.mCmdParamsFactory = CommandParamsFactory.getInstance(this, iccFileHandler);
    }

    private boolean decodeMessageParams(RilMessage rilMessage) {
        this.mCurrentRilMessage = rilMessage;
        switch (rilMessage.mId) {
            case 1:
            case 4:
                this.mCurrentRilMessage.mResCode = ResultCode.OK;
                sendCmdForExecution(this.mCurrentRilMessage);
                return false;
            case 2:
            case 3:
            case 5:
                try {
                    try {
                        this.mCmdParamsFactory.make(BerTlv.decode(IccUtils.hexStringToBytes((String) rilMessage.mData)));
                        return true;
                    } catch (ResultException e) {
                        CatLog.d((Object) this, "decodeMessageParams: caught ResultException e=" + e);
                        this.mCurrentRilMessage.mResCode = e.result();
                        sendCmdForExecution(this.mCurrentRilMessage);
                        return false;
                    }
                } catch (Exception e2) {
                    CatLog.d((Object) this, "decodeMessageParams dropping zombie messages");
                    return false;
                }
            default:
                return false;
        }
    }

    public static RilMessageDecoder getInstance(Handler handler, IccFileHandler iccFileHandler, int i) {
        RilMessageDecoder rilMessageDecoder = null;
        synchronized (RilMessageDecoder.class) {
            try {
                if (mInstance == null) {
                    mSimCount = TelephonyManager.getDefault().getSimCount();
                    mInstance = new RilMessageDecoder[mSimCount];
                    for (int i2 = 0; i2 < mSimCount; i2++) {
                        mInstance[i2] = null;
                    }
                }
                if (i == -1 || i >= mSimCount) {
                    CatLog.d("RilMessageDecoder", "invaild slot id: " + i);
                } else {
                    if (mInstance[i] == null) {
                        mInstance[i] = new RilMessageDecoder(handler, iccFileHandler);
                    }
                    rilMessageDecoder = mInstance[i];
                }
            } catch (Throwable th) {
                Class cls = RilMessageDecoder.class;
            }
        }
        return rilMessageDecoder;
    }

    private void sendCmdForExecution(RilMessage rilMessage) {
        this.mCaller.obtainMessage(9, new RilMessage(rilMessage)).sendToTarget();
    }

    public void dispose(int i) {
        quitNow();
        this.mStateStart = null;
        this.mStateCmdParamsReady = null;
        this.mCmdParamsFactory.dispose();
        this.mCmdParamsFactory = null;
        this.mCurrentRilMessage = null;
        this.mCaller = null;
        mInstance[i] = null;
    }

    public void sendMsgParamsDecoded(ResultCode resultCode, CommandParams commandParams) {
        Message obtainMessage = obtainMessage(2);
        obtainMessage.arg1 = resultCode.value();
        obtainMessage.obj = commandParams;
        sendMessage(obtainMessage);
    }

    public void sendStartDecodingMessageParams(RilMessage rilMessage) {
        Message obtainMessage = obtainMessage(1);
        obtainMessage.obj = rilMessage;
        sendMessage(obtainMessage);
    }
}
