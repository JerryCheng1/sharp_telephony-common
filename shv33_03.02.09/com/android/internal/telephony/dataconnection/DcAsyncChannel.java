package com.android.internal.telephony.dataconnection;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams;
import com.android.internal.telephony.dataconnection.DataConnection.DisconnectParams;
import com.android.internal.util.AsyncChannel;

public class DcAsyncChannel extends AsyncChannel {
    public static final int BASE = 266240;
    private static final int CMD_TO_STRING_COUNT = 14;
    private static boolean DBG = false;
    public static final int REQ_GET_APNSETTING = 266244;
    public static final int REQ_GET_CID = 266242;
    public static final int REQ_GET_LINK_PROPERTIES = 266246;
    public static final int REQ_GET_NETWORK_CAPABILITIES = 266250;
    public static final int REQ_IS_INACTIVE = 266240;
    public static final int REQ_RESET = 266252;
    public static final int REQ_SET_LINK_PROPERTIES_HTTP_PROXY = 266248;
    public static final int RSP_GET_APNSETTING = 266245;
    public static final int RSP_GET_CID = 266243;
    public static final int RSP_GET_LINK_PROPERTIES = 266247;
    public static final int RSP_GET_NETWORK_CAPABILITIES = 266251;
    public static final int RSP_IS_INACTIVE = 266241;
    public static final int RSP_RESET = 266253;
    public static final int RSP_SET_LINK_PROPERTIES_HTTP_PROXY = 266249;
    private static String[] sCmdToString = new String[14];
    private DataConnection mDc;
    private long mDcThreadId = this.mDc.getHandler().getLooper().getThread().getId();
    private String mLogTag;

    public enum LinkPropertyChangeAction {
        NONE,
        CHANGED,
        RESET;

        public static LinkPropertyChangeAction fromInt(int value) {
            if (value == NONE.ordinal()) {
                return NONE;
            }
            if (value == CHANGED.ordinal()) {
                return CHANGED;
            }
            if (value == RESET.ordinal()) {
                return RESET;
            }
            throw new RuntimeException("LinkPropertyChangeAction.fromInt: bad value=" + value);
        }
    }

    static {
        sCmdToString[0] = "REQ_IS_INACTIVE";
        sCmdToString[1] = "RSP_IS_INACTIVE";
        sCmdToString[2] = "REQ_GET_CID";
        sCmdToString[3] = "RSP_GET_CID";
        sCmdToString[4] = "REQ_GET_APNSETTING";
        sCmdToString[5] = "RSP_GET_APNSETTING";
        sCmdToString[6] = "REQ_GET_LINK_PROPERTIES";
        sCmdToString[7] = "RSP_GET_LINK_PROPERTIES";
        sCmdToString[8] = "REQ_SET_LINK_PROPERTIES_HTTP_PROXY";
        sCmdToString[9] = "RSP_SET_LINK_PROPERTIES_HTTP_PROXY";
        sCmdToString[10] = "REQ_GET_NETWORK_CAPABILITIES";
        sCmdToString[11] = "RSP_GET_NETWORK_CAPABILITIES";
        sCmdToString[12] = "REQ_RESET";
        sCmdToString[13] = "RSP_RESET";
    }

    protected static String cmdToString(int cmd) {
        cmd -= 266240;
        if (cmd < 0 || cmd >= sCmdToString.length) {
            return AsyncChannel.cmdToString(cmd + 266240);
        }
        return sCmdToString[cmd];
    }

    public DcAsyncChannel(DataConnection dc, String logTag) {
        this.mDc = dc;
        this.mLogTag = logTag;
        if ("eng".equals(Build.TYPE)) {
            DBG = true;
        }
    }

    public void reqIsInactive() {
        sendMessage(266240);
        if (DBG) {
            log("reqIsInactive");
        }
    }

    public boolean rspIsInactive(Message response) {
        boolean retVal = response.arg1 == 1;
        if (DBG) {
            log("rspIsInactive=" + retVal);
        }
        return retVal;
    }

    public boolean isInactiveSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getIsInactive();
        }
        Message response = sendMessageSynchronously(266240);
        if (response != null && response.what == RSP_IS_INACTIVE) {
            return rspIsInactive(response);
        }
        log("rspIsInactive error response=" + response);
        return false;
    }

    public void reqCid() {
        sendMessage(REQ_GET_CID);
        if (DBG) {
            log("reqCid");
        }
    }

    public int rspCid(Message response) {
        int retVal = response.arg1;
        if (DBG) {
            log("rspCid=" + retVal);
        }
        return retVal;
    }

    public int getCidSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCid();
        }
        Message response = sendMessageSynchronously(REQ_GET_CID);
        if (response != null && response.what == RSP_GET_CID) {
            return rspCid(response);
        }
        log("rspCid error response=" + response);
        return -1;
    }

    public void reqApnSetting() {
        sendMessage(REQ_GET_APNSETTING);
        if (DBG) {
            log("reqApnSetting");
        }
    }

    public ApnSetting rspApnSetting(Message response) {
        ApnSetting retVal = response.obj;
        if (DBG) {
            log("rspApnSetting=" + retVal);
        }
        return retVal;
    }

    public ApnSetting getApnSettingSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getApnSetting();
        }
        Message response = sendMessageSynchronously(REQ_GET_APNSETTING);
        if (response != null && response.what == RSP_GET_APNSETTING) {
            return rspApnSetting(response);
        }
        log("getApnSetting error response=" + response);
        return null;
    }

    public void reqLinkProperties() {
        sendMessage(REQ_GET_LINK_PROPERTIES);
        if (DBG) {
            log("reqLinkProperties");
        }
    }

    public LinkProperties rspLinkProperties(Message response) {
        LinkProperties retVal = response.obj;
        if (DBG) {
            log("rspLinkProperties=" + retVal);
        }
        return retVal;
    }

    public LinkProperties getLinkPropertiesSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCopyLinkProperties();
        }
        Message response = sendMessageSynchronously(REQ_GET_LINK_PROPERTIES);
        if (response != null && response.what == RSP_GET_LINK_PROPERTIES) {
            return rspLinkProperties(response);
        }
        log("getLinkProperties error response=" + response);
        return null;
    }

    public void reqSetLinkPropertiesHttpProxy(ProxyInfo proxy) {
        sendMessage(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxy);
        if (DBG) {
            log("reqSetLinkPropertiesHttpProxy proxy=" + proxy);
        }
    }

    public void setLinkPropertiesHttpProxySync(ProxyInfo proxy) {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxy);
            if (response == null || response.what != RSP_SET_LINK_PROPERTIES_HTTP_PROXY) {
                log("setLinkPropertiesHttpPoxy error response=" + response);
                return;
            } else if (DBG) {
                log("setLinkPropertiesHttpPoxy ok");
                return;
            } else {
                return;
            }
        }
        this.mDc.setLinkPropertiesHttpProxy(proxy);
    }

    public void reqNetworkCapabilities() {
        sendMessage(REQ_GET_NETWORK_CAPABILITIES);
        if (DBG) {
            log("reqNetworkCapabilities");
        }
    }

    public NetworkCapabilities rspNetworkCapabilities(Message response) {
        NetworkCapabilities retVal = response.obj;
        if (DBG) {
            log("rspNetworkCapabilities=" + retVal);
        }
        return retVal;
    }

    public NetworkCapabilities getNetworkCapabilitiesSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCopyNetworkCapabilities();
        }
        Message response = sendMessageSynchronously(REQ_GET_NETWORK_CAPABILITIES);
        if (response == null || response.what != RSP_GET_NETWORK_CAPABILITIES) {
            return null;
        }
        return rspNetworkCapabilities(response);
    }

    public void reqReset() {
        sendMessage(REQ_RESET);
        if (DBG) {
            log("reqReset");
        }
    }

    public void bringUp(ApnContext apnContext, int profileId, int rilRadioTechnology, Message onCompletedMsg, int connectionGeneration) {
        if (DBG) {
            log("bringUp: apnContext=" + apnContext + " onCompletedMsg=" + onCompletedMsg);
        }
        sendMessage(SmsEnvelope.TELESERVICE_MWI, new ConnectionParams(apnContext, profileId, rilRadioTechnology, onCompletedMsg, connectionGeneration));
    }

    public void tearDown(ApnContext apnContext, String reason, Message onCompletedMsg) {
        if (DBG) {
            log("tearDown: apnContext=" + apnContext + " reason=" + reason + " onCompletedMsg=" + onCompletedMsg);
        }
        sendMessage(262148, new DisconnectParams(apnContext, reason, onCompletedMsg));
    }

    public void tearDownAll(String reason, Message onCompletedMsg) {
        if (DBG) {
            log("tearDownAll: reason=" + reason + " onCompletedMsg=" + onCompletedMsg);
        }
        sendMessage(262150, new DisconnectParams(null, reason, onCompletedMsg));
    }

    public int getDataConnectionIdSync() {
        return this.mDc.getDataConnectionId();
    }

    public String toString() {
        return this.mDc.getName();
    }

    private boolean isCallerOnDifferentThread() {
        boolean value = this.mDcThreadId != Thread.currentThread().getId();
        if (DBG) {
            log("isCallerOnDifferentThread: " + value);
        }
        return value;
    }

    private void log(String s) {
        Rlog.d(this.mLogTag, "DataConnectionAc " + s);
    }

    public String[] getPcscfAddr() {
        return this.mDc.mPcscfAddr;
    }
}
