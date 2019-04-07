package com.android.internal.telephony.dataconnection;

import android.net.NetworkRequest;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.util.AsyncChannel;

public class DcSwitchAsyncChannel extends AsyncChannel {
    private static final int BASE = 278528;
    private static final int CMD_TO_STRING_COUNT = 12;
    private static final boolean DBG = true;
    static final int EVENT_DATA_ATTACHED = 278538;
    static final int EVENT_DATA_DETACHED = 278539;
    private static final String LOG_TAG = "DcSwitchAsyncChannel";
    static final int REQ_CONNECT = 278528;
    static final int REQ_DISCONNECT = 278530;
    static final int REQ_DISCONNECT_ALL = 278532;
    static final int REQ_IS_IDLE_OR_DETACHING_STATE = 278536;
    static final int REQ_IS_IDLE_STATE = 278534;
    static final int RSP_CONNECT = 278529;
    static final int RSP_DISCONNECT = 278531;
    static final int RSP_DISCONNECT_ALL = 278533;
    static final int RSP_IS_IDLE_OR_DETACHING_STATE = 278537;
    static final int RSP_IS_IDLE_STATE = 278535;
    private static final boolean VDBG = false;
    private static String[] sCmdToString = new String[12];
    private DcSwitchStateMachine mDcSwitchState;
    private int tagId = 0;

    public static class RequestInfo {
        boolean executed;
        int priority;
        NetworkRequest request;

        public RequestInfo(NetworkRequest networkRequest, int i) {
            this.request = networkRequest;
            this.priority = i;
        }

        public String toString() {
            return "[ request=" + this.request + ", executed=" + this.executed + ", priority=" + this.priority + "]";
        }
    }

    static {
        sCmdToString[0] = "REQ_CONNECT";
        sCmdToString[1] = "RSP_CONNECT";
        sCmdToString[2] = "REQ_DISCONNECT";
        sCmdToString[3] = "RSP_DISCONNECT";
        sCmdToString[4] = "REQ_DISCONNECT_ALL";
        sCmdToString[5] = "RSP_DISCONNECT_ALL";
        sCmdToString[6] = "REQ_IS_IDLE_STATE";
        sCmdToString[7] = "RSP_IS_IDLE_STATE";
        sCmdToString[8] = "REQ_IS_IDLE_OR_DETACHING_STATE";
        sCmdToString[9] = "RSP_IS_IDLE_OR_DETACHING_STATE";
        sCmdToString[10] = "EVENT_DATA_ATTACHED";
        sCmdToString[11] = "EVENT_DATA_DETACHED";
    }

    public DcSwitchAsyncChannel(DcSwitchStateMachine dcSwitchStateMachine, int i) {
        this.mDcSwitchState = dcSwitchStateMachine;
        this.tagId = i;
    }

    protected static String cmdToString(int i) {
        int i2 = i - 278528;
        return (i2 < 0 || i2 >= sCmdToString.length) ? AsyncChannel.cmdToString(i2 + 278528) : sCmdToString[i2];
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, "[DcSwitchAsyncChannel-" + this.tagId + "]: " + str);
    }

    private int rspConnect(Message message) {
        int i = message.arg1;
        log("rspConnect=" + i);
        return i;
    }

    private int rspDisconnect(Message message) {
        int i = message.arg1;
        log("rspDisconnect=" + i);
        return i;
    }

    private int rspDisconnectAll(Message message) {
        int i = message.arg1;
        log("rspDisconnectAll=" + i);
        return i;
    }

    private boolean rspIsIdle(Message message) {
        boolean z = true;
        if (message.arg1 != 1) {
            z = false;
        }
        log("rspIsIdle=" + z);
        return z;
    }

    public int connectSync(RequestInfo requestInfo) {
        Message sendMessageSynchronously = sendMessageSynchronously(278528, requestInfo);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_CONNECT) {
            return rspConnect(sendMessageSynchronously);
        }
        log("rspConnect error response=" + sendMessageSynchronously);
        return 3;
    }

    public int disconnectAllSync() {
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_DISCONNECT_ALL);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_DISCONNECT_ALL) {
            return rspDisconnectAll(sendMessageSynchronously);
        }
        log("rspDisconnectAll error response=" + sendMessageSynchronously);
        return 3;
    }

    public int disconnectSync(RequestInfo requestInfo) {
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_DISCONNECT, requestInfo);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_DISCONNECT) {
            return rspDisconnect(sendMessageSynchronously);
        }
        log("rspDisconnect error response=" + sendMessageSynchronously);
        return 3;
    }

    public boolean isIdleOrDetachingSync() {
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_IS_IDLE_OR_DETACHING_STATE);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_IS_IDLE_OR_DETACHING_STATE) {
            return rspIsIdleOrDetaching(sendMessageSynchronously);
        }
        log("rspIsIdleOrDetaching error response=" + sendMessageSynchronously);
        return false;
    }

    public boolean isIdleSync() {
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_IS_IDLE_STATE);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_IS_IDLE_STATE) {
            return rspIsIdle(sendMessageSynchronously);
        }
        log("rspIsIndle error response=" + sendMessageSynchronously);
        return false;
    }

    public void notifyDataAttached() {
        sendMessage(EVENT_DATA_ATTACHED);
        log("notifyDataAttached");
    }

    public void notifyDataDetached() {
        sendMessage(EVENT_DATA_DETACHED);
        log("EVENT_DATA_DETACHED");
    }

    public void reqIsIdleOrDetaching() {
        sendMessage(REQ_IS_IDLE_OR_DETACHING_STATE);
        log("reqIsIdleOrDetaching");
    }

    public boolean rspIsIdleOrDetaching(Message message) {
        boolean z = true;
        if (message.arg1 != 1) {
            z = false;
        }
        log("rspIsIdleOrDetaching=" + z);
        return z;
    }

    public String toString() {
        return this.mDcSwitchState.getName();
    }
}
