package com.android.internal.telephony.dataconnection;

import android.net.NetworkRequest;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.util.AsyncChannel;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private int tagId;

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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class RequestInfo {
        boolean executed;
        int priority;
        NetworkRequest request;

        public RequestInfo(NetworkRequest request, int priority) {
            this.request = request;
            this.priority = priority;
        }

        public String toString() {
            return "[ request=" + this.request + ", executed=" + this.executed + ", priority=" + this.priority + "]";
        }
    }

    protected static String cmdToString(int cmd) {
        int cmd2 = cmd - 278528;
        if (cmd2 < 0 || cmd2 >= sCmdToString.length) {
            return AsyncChannel.cmdToString(cmd2 + 278528);
        }
        return sCmdToString[cmd2];
    }

    public DcSwitchAsyncChannel(DcSwitchStateMachine dcSwitchState, int id) {
        this.tagId = 0;
        this.mDcSwitchState = dcSwitchState;
        this.tagId = id;
    }

    private int rspConnect(Message response) {
        int retVal = response.arg1;
        log("rspConnect=" + retVal);
        return retVal;
    }

    public int connectSync(RequestInfo apnRequest) {
        Message response = sendMessageSynchronously(278528, apnRequest);
        if (response != null && response.what == RSP_CONNECT) {
            return rspConnect(response);
        }
        log("rspConnect error response=" + response);
        return 3;
    }

    private int rspDisconnect(Message response) {
        int retVal = response.arg1;
        log("rspDisconnect=" + retVal);
        return retVal;
    }

    public int disconnectSync(RequestInfo apnRequest) {
        Message response = sendMessageSynchronously(REQ_DISCONNECT, apnRequest);
        if (response != null && response.what == RSP_DISCONNECT) {
            return rspDisconnect(response);
        }
        log("rspDisconnect error response=" + response);
        return 3;
    }

    private int rspDisconnectAll(Message response) {
        int retVal = response.arg1;
        log("rspDisconnectAll=" + retVal);
        return retVal;
    }

    public int disconnectAllSync() {
        Message response = sendMessageSynchronously(REQ_DISCONNECT_ALL);
        if (response != null && response.what == RSP_DISCONNECT_ALL) {
            return rspDisconnectAll(response);
        }
        log("rspDisconnectAll error response=" + response);
        return 3;
    }

    public void notifyDataAttached() {
        sendMessage(EVENT_DATA_ATTACHED);
        log("notifyDataAttached");
    }

    public void notifyDataDetached() {
        sendMessage(EVENT_DATA_DETACHED);
        log("EVENT_DATA_DETACHED");
    }

    private boolean rspIsIdle(Message response) {
        boolean retVal = true;
        if (response.arg1 != 1) {
            retVal = false;
        }
        log("rspIsIdle=" + retVal);
        return retVal;
    }

    public boolean isIdleSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_STATE);
        if (response != null && response.what == RSP_IS_IDLE_STATE) {
            return rspIsIdle(response);
        }
        log("rspIsIndle error response=" + response);
        return false;
    }

    public void reqIsIdleOrDetaching() {
        sendMessage(REQ_IS_IDLE_OR_DETACHING_STATE);
        log("reqIsIdleOrDetaching");
    }

    public boolean rspIsIdleOrDetaching(Message response) {
        boolean retVal = true;
        if (response.arg1 != 1) {
            retVal = false;
        }
        log("rspIsIdleOrDetaching=" + retVal);
        return retVal;
    }

    public boolean isIdleOrDetachingSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_OR_DETACHING_STATE);
        if (response != null && response.what == RSP_IS_IDLE_OR_DETACHING_STATE) {
            return rspIsIdleOrDetaching(response);
        }
        log("rspIsIdleOrDetaching error response=" + response);
        return false;
    }

    public String toString() {
        return this.mDcSwitchState.getName();
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, "[DcSwitchAsyncChannel-" + this.tagId + "]: " + s);
    }
}
