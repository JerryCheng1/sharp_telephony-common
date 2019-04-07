package com.android.internal.telephony.dataconnection;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
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

        public static LinkPropertyChangeAction fromInt(int i) {
            if (i == NONE.ordinal()) {
                return NONE;
            }
            if (i == CHANGED.ordinal()) {
                return CHANGED;
            }
            if (i == RESET.ordinal()) {
                return RESET;
            }
            throw new RuntimeException("LinkPropertyChangeAction.fromInt: bad value=" + i);
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

    public DcAsyncChannel(DataConnection dataConnection, String str) {
        this.mDc = dataConnection;
        this.mLogTag = str;
        if ("eng".equals(Build.TYPE)) {
            DBG = true;
        }
    }

    protected static String cmdToString(int i) {
        int i2 = i - 266240;
        return (i2 < 0 || i2 >= sCmdToString.length) ? AsyncChannel.cmdToString(i2 + 266240) : sCmdToString[i2];
    }

    private boolean isCallerOnDifferentThread() {
        boolean z = this.mDcThreadId != Thread.currentThread().getId();
        if (DBG) {
            log("isCallerOnDifferentThread: " + z);
        }
        return z;
    }

    private void log(String str) {
        Rlog.d(this.mLogTag, "DataConnectionAc " + str);
    }

    public void bringUp(ApnContext apnContext, int i, int i2, int i3, boolean z, Message message) {
        if (DBG) {
            log("bringUp: apnContext=" + apnContext + " initialMaxRetry=" + i + " onCompletedMsg=" + message);
        }
        sendMessage(SmsEnvelope.TELESERVICE_MWI, new ConnectionParams(apnContext, i, i2, i3, z, message));
    }

    public ApnSetting getApnSettingSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getApnSetting();
        }
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_GET_APNSETTING);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_GET_APNSETTING) {
            return rspApnSetting(sendMessageSynchronously);
        }
        log("getApnSetting error response=" + sendMessageSynchronously);
        return null;
    }

    public int getCidSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCid();
        }
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_GET_CID);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_GET_CID) {
            return rspCid(sendMessageSynchronously);
        }
        log("rspCid error response=" + sendMessageSynchronously);
        return -1;
    }

    public int getDataConnectionIdSync() {
        return this.mDc.getDataConnectionId();
    }

    public LinkProperties getLinkPropertiesSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCopyLinkProperties();
        }
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_GET_LINK_PROPERTIES);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_GET_LINK_PROPERTIES) {
            return rspLinkProperties(sendMessageSynchronously);
        }
        log("getLinkProperties error response=" + sendMessageSynchronously);
        return null;
    }

    public NetworkCapabilities getNetworkCapabilitiesSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getCopyNetworkCapabilities();
        }
        Message sendMessageSynchronously = sendMessageSynchronously(REQ_GET_NETWORK_CAPABILITIES);
        return (sendMessageSynchronously == null || sendMessageSynchronously.what != RSP_GET_NETWORK_CAPABILITIES) ? null : rspNetworkCapabilities(sendMessageSynchronously);
    }

    public String[] getPcscfAddr() {
        return this.mDc.mPcscfAddr;
    }

    public boolean isInactiveSync() {
        if (!isCallerOnDifferentThread()) {
            return this.mDc.getIsInactive();
        }
        Message sendMessageSynchronously = sendMessageSynchronously(266240);
        if (sendMessageSynchronously != null && sendMessageSynchronously.what == RSP_IS_INACTIVE) {
            return rspIsInactive(sendMessageSynchronously);
        }
        log("rspIsInactive error response=" + sendMessageSynchronously);
        return false;
    }

    public void reqApnSetting() {
        sendMessage(REQ_GET_APNSETTING);
        if (DBG) {
            log("reqApnSetting");
        }
    }

    public void reqCid() {
        sendMessage(REQ_GET_CID);
        if (DBG) {
            log("reqCid");
        }
    }

    public void reqIsInactive() {
        sendMessage(266240);
        if (DBG) {
            log("reqIsInactive");
        }
    }

    public void reqLinkProperties() {
        sendMessage(REQ_GET_LINK_PROPERTIES);
        if (DBG) {
            log("reqLinkProperties");
        }
    }

    public void reqNetworkCapabilities() {
        sendMessage(REQ_GET_NETWORK_CAPABILITIES);
        if (DBG) {
            log("reqNetworkCapabilities");
        }
    }

    public void reqReset() {
        sendMessage(REQ_RESET);
        if (DBG) {
            log("reqReset");
        }
    }

    public void reqSetLinkPropertiesHttpProxy(ProxyInfo proxyInfo) {
        sendMessage(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxyInfo);
        if (DBG) {
            log("reqSetLinkPropertiesHttpProxy proxy=" + proxyInfo);
        }
    }

    public ApnSetting rspApnSetting(Message message) {
        ApnSetting apnSetting = (ApnSetting) message.obj;
        if (DBG) {
            log("rspApnSetting=" + apnSetting);
        }
        return apnSetting;
    }

    public int rspCid(Message message) {
        int i = message.arg1;
        if (DBG) {
            log("rspCid=" + i);
        }
        return i;
    }

    public boolean rspIsInactive(Message message) {
        boolean z = true;
        if (message.arg1 != 1) {
            z = false;
        }
        if (DBG) {
            log("rspIsInactive=" + z);
        }
        return z;
    }

    public LinkProperties rspLinkProperties(Message message) {
        LinkProperties linkProperties = (LinkProperties) message.obj;
        if (DBG) {
            log("rspLinkProperties=" + linkProperties);
        }
        return linkProperties;
    }

    public NetworkCapabilities rspNetworkCapabilities(Message message) {
        NetworkCapabilities networkCapabilities = (NetworkCapabilities) message.obj;
        if (DBG) {
            log("rspNetworkCapabilities=" + networkCapabilities);
        }
        return networkCapabilities;
    }

    public void setLinkPropertiesHttpProxySync(ProxyInfo proxyInfo) {
        if (isCallerOnDifferentThread()) {
            Message sendMessageSynchronously = sendMessageSynchronously(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxyInfo);
            if (sendMessageSynchronously == null || sendMessageSynchronously.what != RSP_SET_LINK_PROPERTIES_HTTP_PROXY) {
                log("setLinkPropertiesHttpPoxy error response=" + sendMessageSynchronously);
                return;
            } else if (DBG) {
                log("setLinkPropertiesHttpPoxy ok");
                return;
            } else {
                return;
            }
        }
        this.mDc.setLinkPropertiesHttpProxy(proxyInfo);
    }

    public void tearDown(ApnContext apnContext, String str, Message message) {
        if (DBG) {
            log("tearDown: apnContext=" + apnContext + " reason=" + str + " onCompletedMsg=" + message);
        }
        sendMessage(262148, new DisconnectParams(apnContext, str, message));
    }

    public void tearDownAll(String str, Message message) {
        if (DBG) {
            log("tearDownAll: reason=" + str + " onCompletedMsg=" + message);
        }
        sendMessage(262150, new DisconnectParams(null, str, message));
    }

    public String toString() {
        return this.mDc.getName();
    }
}
