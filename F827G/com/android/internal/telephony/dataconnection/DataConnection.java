package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkMisc;
import android.net.ProxyInfo;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Patterns;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.google.android.mms.pdu.CharacterSets;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class DataConnection extends StateMachine {
    static final int BASE = 262144;
    private static final int CMD_TO_STRING_COUNT = 14;
    private static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    private static final String DEFAULT_DATA_RETRY_CONFIG_KDDI = "default_randomization=0,26000,52000,104000,208000,416000,832000,1664000,1800000";
    private static final String DEFAULT_DATA_RETRY_CONFIG_SBM = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000,3600000:5000";
    static final int EVENT_CONNECT = 262144;
    static final int EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED = 262155;
    static final int EVENT_DATA_CONNECTION_ROAM_OFF = 262157;
    static final int EVENT_DATA_CONNECTION_ROAM_ON = 262156;
    static final int EVENT_DATA_STATE_CHANGED = 262151;
    static final int EVENT_DEACTIVATE_DONE = 262147;
    static final int EVENT_DISCONNECT = 262148;
    static final int EVENT_DISCONNECT_ALL = 262150;
    static final int EVENT_GET_LAST_FAIL_DONE = 262146;
    static final int EVENT_LOST_CONNECTION = 262153;
    static final int EVENT_RETRY_CONNECTION = 262154;
    static final int EVENT_RIL_CONNECTED = 262149;
    static final int EVENT_SETUP_DATA_CONNECTION_DONE = 262145;
    static final int EVENT_TEAR_DOWN_NOW = 262152;
    private static final String NETWORK_TYPE = "MOBILE";
    private static final String NULL_IP = "0.0.0.0";
    private static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    private static final String TCP_BUFFER_SIZES_1XRTT = "16384,32768,131072,4096,16384,102400";
    private static final String TCP_BUFFER_SIZES_EDGE = "4093,26280,70800,4096,16384,70800";
    private static final String TCP_BUFFER_SIZES_EHRPD = "131072,262144,1048576,4096,16384,524288";
    private static final String TCP_BUFFER_SIZES_EVDO = "4094,87380,262144,4096,16384,262144";
    private static final String TCP_BUFFER_SIZES_GPRS = "4092,8760,48000,4096,8760,48000";
    private static final String TCP_BUFFER_SIZES_HSDPA = "61167,367002,1101005,8738,52429,262114";
    private static final String TCP_BUFFER_SIZES_HSPA = "40778,244668,734003,16777,100663,301990";
    private static final String TCP_BUFFER_SIZES_HSPAP = "122334,734003,2202010,32040,192239,576717";
    private static final String TCP_BUFFER_SIZES_LTE = "524288,1048576,2097152,262144,524288,1048576";
    private static final String TCP_BUFFER_SIZES_UMTS = "58254,349525,1048576,58254,349525,1048576";
    private AsyncChannel mAc;
    List<ApnContext> mApnContexts;
    private ApnSetting mApnSetting;
    int mCid;
    private ConnectionParams mConnectionParams;
    private long mCreateTime;
    private int mDataRegState;
    private DcController mDcController;
    private DcFailCause mDcFailCause;
    private DcRetryAlarmController mDcRetryAlarmController;
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcTrackerBase mDct;
    private DisconnectParams mDisconnectParams;
    private int mId;
    private DcFailCause mLastFailCause;
    private long mLastFailTime;
    private NetworkAgent mNetworkAgent;
    private NetworkInfo mNetworkInfo;
    protected String[] mPcscfAddr;
    private PhoneBase mPhone;
    private int mRilRat;
    int mTag;
    private Object mUserData;
    private static boolean DBG = true;
    private static boolean VDBG = true;
    private static AtomicInteger mInstanceNumber = new AtomicInteger(0);
    private static String[] sCmdToString = new String[14];
    private LinkProperties mLinkProperties = new LinkProperties();
    PendingIntent mReconnectIntent = null;
    RetryManager mRetryManager = new RetryManager();
    private DcDefaultState mDefaultState = new DcDefaultState();
    private DcInactiveState mInactiveState = new DcInactiveState();
    private DcRetryingState mRetryingState = new DcRetryingState();
    private DcActivatingState mActivatingState = new DcActivatingState();
    private DcActiveState mActiveState = new DcActiveState();
    private DcDisconnectingState mDisconnectingState = new DcDisconnectingState();
    private DcDisconnectionErrorCreatingConnection mDisconnectingErrorCreatingConnection = new DcDisconnectionErrorCreatingConnection();

    static {
        sCmdToString[0] = "EVENT_CONNECT";
        sCmdToString[1] = "EVENT_SETUP_DATA_CONNECTION_DONE";
        sCmdToString[2] = "EVENT_GET_LAST_FAIL_DONE";
        sCmdToString[3] = "EVENT_DEACTIVATE_DONE";
        sCmdToString[4] = "EVENT_DISCONNECT";
        sCmdToString[5] = "EVENT_RIL_CONNECTED";
        sCmdToString[6] = "EVENT_DISCONNECT_ALL";
        sCmdToString[7] = "EVENT_DATA_STATE_CHANGED";
        sCmdToString[8] = "EVENT_TEAR_DOWN_NOW";
        sCmdToString[9] = "EVENT_LOST_CONNECTION";
        sCmdToString[10] = "EVENT_RETRY_CONNECTION";
        sCmdToString[11] = "EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED";
        sCmdToString[12] = "EVENT_DATA_CONNECTION_ROAM_ON";
        sCmdToString[13] = "EVENT_DATA_CONNECTION_ROAM_OFF";
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class ConnectionParams {
        ApnContext mApnContext;
        int mInitialMaxRetry;
        Message mOnCompletedMsg;
        int mProfileId;
        boolean mRetryWhenSSChange;
        int mRilRat;
        int mTag;

        public ConnectionParams(ApnContext apnContext, int initialMaxRetry, int profileId, int rilRadioTechnology, boolean retryWhenSSChange, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mInitialMaxRetry = initialMaxRetry;
            this.mProfileId = profileId;
            this.mRilRat = rilRadioTechnology;
            this.mRetryWhenSSChange = retryWhenSSChange;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mInitialMaxRetry=" + this.mInitialMaxRetry + " mProfileId=" + this.mProfileId + " mRat=" + this.mRilRat + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class DisconnectParams {
        ApnContext mApnContext;
        Message mOnCompletedMsg;
        String mReason;
        int mTag;

        public DisconnectParams(ApnContext apnContext, String reason, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mReason = reason;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mReason=" + this.mReason + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    public static String cmdToString(int cmd) {
        String value;
        int cmd2 = cmd - SmsEnvelope.TELESERVICE_MWI;
        if (cmd2 < 0 || cmd2 >= sCmdToString.length) {
            value = DcAsyncChannel.cmdToString(cmd2 + SmsEnvelope.TELESERVICE_MWI);
        } else {
            value = sCmdToString[cmd2];
        }
        if (value == null) {
            return "0x" + Integer.toHexString(cmd2 + SmsEnvelope.TELESERVICE_MWI);
        }
        return value;
    }

    public static DataConnection makeDataConnection(PhoneBase phone, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        DataConnection dc = new DataConnection(phone, "DC-" + mInstanceNumber.incrementAndGet(), id, dct, failBringUpAll, dcc);
        dc.start();
        if (DBG) {
            dc.log("Made " + dc.getName());
        }
        return dc;
    }

    void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    public NetworkCapabilities getCopyNetworkCapabilities() {
        return makeNetworkCapabilities();
    }

    public LinkProperties getCopyLinkProperties() {
        return new LinkProperties(this.mLinkProperties);
    }

    public boolean getIsInactive() {
        return getCurrentState() == this.mInactiveState;
    }

    public int getCid() {
        return this.mCid;
    }

    public ApnSetting getApnSetting() {
        return this.mApnSetting;
    }

    public void setLinkPropertiesHttpProxy(ProxyInfo proxy) {
        this.mLinkProperties.setHttpProxy(proxy);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class UpdateLinkPropertyResult {
        public LinkProperties newLp;
        public LinkProperties oldLp;
        public DataCallResponse.SetupResult setupResult = DataCallResponse.SetupResult.SUCCESS;

        public UpdateLinkPropertyResult(LinkProperties curLp) {
            this.oldLp = curLp;
            this.newLp = curLp;
        }
    }

    public boolean isIpv4Connected() {
        for (InetAddress addr : this.mLinkProperties.getAddresses()) {
            if (addr instanceof Inet4Address) {
                Inet4Address i4addr = (Inet4Address) addr;
                if (!i4addr.isAnyLocalAddress() && !i4addr.isLinkLocalAddress() && !i4addr.isLoopbackAddress() && !i4addr.isMulticastAddress()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isIpv6Connected() {
        for (InetAddress addr : this.mLinkProperties.getAddresses()) {
            if (addr instanceof Inet6Address) {
                Inet6Address i6addr = (Inet6Address) addr;
                if (!i6addr.isAnyLocalAddress() && !i6addr.isLinkLocalAddress() && !i6addr.isLoopbackAddress() && !i6addr.isMulticastAddress()) {
                    return true;
                }
            }
        }
        return false;
    }

    public UpdateLinkPropertyResult updateLinkProperty(DataCallResponse newState) {
        UpdateLinkPropertyResult result = new UpdateLinkPropertyResult(this.mLinkProperties);
        if (newState != null) {
            result.newLp = new LinkProperties();
            if (TelBrand.IS_KDDI) {
                if (this.mApnSetting.oemDnses != null) {
                    ArrayList<String> dnses = new ArrayList<>();
                    String[] arr$ = this.mApnSetting.oemDnses;
                    for (String addr : arr$) {
                        if (addr != null) {
                            String addr2 = addr.trim();
                            if (!addr2.isEmpty()) {
                                dnses.add(addr2);
                            }
                        }
                    }
                    boolean isValidNetworkDns = false;
                    if (newState.dnses != null) {
                        String[] arr$2 = newState.dnses;
                        int len$ = arr$2.length;
                        int i$ = 0;
                        while (true) {
                            if (i$ >= len$) {
                                break;
                            }
                            String addr3 = arr$2[i$];
                            if (addr3 != null && !addr3.trim().isEmpty()) {
                                isValidNetworkDns = true;
                                break;
                            }
                            i$++;
                        }
                    }
                    if (!isValidNetworkDns && dnses.size() == 0) {
                        dnses.add("");
                    }
                    if (dnses.size() != 0) {
                        newState.dnses = (String[]) dnses.toArray(new String[0]);
                    }
                }
                if (this.mPhone.mDcTracker.mEnableApnCarNavi) {
                    this.mPhone.mDcTracker.mLocalAddressCarNavi = "";
                    this.mPhone.mDcTracker.mDnsAddressCarNavi[0] = "";
                    this.mPhone.mDcTracker.mDnsAddressCarNavi[1] = "";
                    if (newState.addresses != null && newState.addresses.length > 0) {
                        for (String addr4 : newState.addresses) {
                            String addr5 = addr4.trim();
                            if (!addr5.isEmpty()) {
                                String[] ap = addr5.split("/");
                                if (ap.length == 2) {
                                    this.mPhone.mDcTracker.mLocalAddressCarNavi = ap[0];
                                }
                            }
                        }
                    }
                    if (newState.dnses != null && newState.dnses.length > 0) {
                        this.mPhone.mDcTracker.mDnsAddressCarNavi[0] = newState.dnses[0].trim();
                    }
                    if (newState.dnses != null && newState.dnses.length > 1) {
                        this.mPhone.mDcTracker.mDnsAddressCarNavi[1] = newState.dnses[1].trim();
                    }
                }
            }
            result.setupResult = setLinkProperties(newState, result.newLp);
            if (result.setupResult == DataCallResponse.SetupResult.SUCCESS) {
                result.newLp.setHttpProxy(this.mLinkProperties.getHttpProxy());
                checkSetMtu(this.mApnSetting, result.newLp);
                if (TelBrand.IS_KDDI && this.mApnSetting.oemDnses != null && (result.newLp.getDnsServers() == null || result.newLp.getDnsServers().size() == 0)) {
                    try {
                        ArrayList<InetAddress> dnsServers = new ArrayList<>();
                        dnsServers.add(InetAddress.getByName(NULL_IP));
                        result.newLp.setDnsServers(dnsServers);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
                this.mLinkProperties = result.newLp;
                updateTcpBufferSizes(this.mRilRat);
                if (DBG && !result.oldLp.equals(result.newLp)) {
                    log("updateLinkProperty old LP=" + result.oldLp);
                    log("updateLinkProperty new LP=" + result.newLp);
                }
                if (!result.newLp.equals(result.oldLp) && this.mNetworkAgent != null) {
                    this.mNetworkAgent.sendLinkProperties(this.mLinkProperties);
                }
            } else if (DBG) {
                log("updateLinkProperty failed : " + result.setupResult);
            }
        }
        return result;
    }

    private void checkSetMtu(ApnSetting apn, LinkProperties lp) {
        if (lp != null && apn != null && lp != null) {
            if (lp.getMtu() != 0) {
                if (DBG) {
                    log("MTU set by call response to: " + lp.getMtu());
                }
            } else if (apn == null || apn.mtu == 0) {
                int mtu = this.mPhone.getContext().getResources().getInteger(17694857);
                if (mtu != 0) {
                    lp.setMtu(mtu);
                    if (DBG) {
                        log("MTU set by config resource to: " + mtu);
                    }
                }
            } else {
                lp.setMtu(apn.mtu);
                if (DBG) {
                    log("MTU set by APN to: " + apn.mtu);
                }
            }
        }
    }

    private DataConnection(PhoneBase phone, String name, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        super(name, dcc.getHandler());
        this.mDct = null;
        this.mRilRat = Integer.MAX_VALUE;
        this.mDataRegState = Integer.MAX_VALUE;
        this.mApnContexts = null;
        if (!"eng".equals(Build.TYPE)) {
            DBG = false;
            VDBG = false;
        }
        setLogRecSize(300);
        setLogOnlyTransitions(true);
        if (DBG) {
            log("DataConnection constructor E");
        }
        this.mPhone = phone;
        this.mDct = dct;
        this.mDcTesterFailBringUpAll = failBringUpAll;
        this.mDcController = dcc;
        this.mId = id;
        this.mCid = -1;
        this.mDcRetryAlarmController = new DcRetryAlarmController(this.mPhone, this);
        ServiceState ss = this.mPhone.getServiceState();
        this.mRilRat = ss.getRilDataRadioTechnology();
        this.mDataRegState = this.mPhone.getServiceState().getDataRegState();
        int networkType = ss.getDataNetworkType();
        this.mNetworkInfo = new NetworkInfo(0, networkType, NETWORK_TYPE, TelephonyManager.getNetworkTypeName(networkType));
        this.mNetworkInfo.setRoaming(ss.getDataRoaming());
        this.mNetworkInfo.setIsAvailable(true);
        addState(this.mDefaultState);
        addState(this.mInactiveState, this.mDefaultState);
        addState(this.mActivatingState, this.mDefaultState);
        addState(this.mRetryingState, this.mDefaultState);
        addState(this.mActiveState, this.mDefaultState);
        addState(this.mDisconnectingState, this.mDefaultState);
        addState(this.mDisconnectingErrorCreatingConnection, this.mDefaultState);
        setInitialState(this.mInactiveState);
        this.mApnContexts = new ArrayList();
        if (DBG) {
            log("DataConnection constructor X");
        }
    }

    private String getRetryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (Build.IS_DEBUGGABLE) {
            String config = SystemProperties.get("test.data_retry_config");
            if (!TextUtils.isEmpty(config)) {
                return config;
            }
        }
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            return SystemProperties.get("ro.cdma.data_retry_config");
        }
        if (forDefault) {
            return SystemProperties.get("ro.gsm.data_retry_config");
        }
        return SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    private void configureRetry(boolean forDefault) {
        if (!this.mRetryManager.configure(getRetryConfig(forDefault))) {
            if (forDefault) {
                if (TelBrand.IS_KDDI) {
                    if (!this.mRetryManager.configure(DEFAULT_DATA_RETRY_CONFIG_KDDI)) {
                        loge("configureRetry: Could not configure using DEFAULT_DATA_RETRY_CONFIG_KDDI=default_randomization=0,26000,52000,104000,208000,416000,832000,1664000,1800000");
                        this.mRetryManager.configure(5, 2000, 1000);
                    }
                } else if (TelBrand.IS_SBM) {
                    if (!this.mRetryManager.configure(DEFAULT_DATA_RETRY_CONFIG_SBM)) {
                        loge("configureRetry: Could not configure using DEFAULT_DATA_RETRY_CONFIG_SBM=default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000,3600000:5000");
                        this.mRetryManager.configure(5, 2000, 1000);
                    }
                } else if (!this.mRetryManager.configure(DEFAULT_DATA_RETRY_CONFIG)) {
                    loge("configureRetry: Could not configure using DEFAULT_DATA_RETRY_CONFIG=default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000");
                    this.mRetryManager.configure(5, 2000, 1000);
                }
            } else if (!this.mRetryManager.configure(SECONDARY_DATA_RETRY_CONFIG)) {
                loge("configureRetry: Could note configure using SECONDARY_DATA_RETRY_CONFIG=max_retries=3, 5000, 5000, 5000");
                this.mRetryManager.configure(5, 2000, 1000);
            }
        }
        if (DBG) {
            log("configureRetry: forDefault=" + forDefault + " mRetryManager=" + this.mRetryManager);
        }
    }

    public void onConnect(ConnectionParams cp) {
        int dataProfileId;
        String protocol;
        if (DBG) {
            log("onConnect: carrier='" + this.mApnSetting.carrier + "' APN='" + this.mApnSetting.apn + "' proxy='" + this.mApnSetting.proxy + "' port='" + this.mApnSetting.port + "'");
        }
        if (this.mDcTesterFailBringUpAll.getDcFailBringUp().mCounter > 0) {
            DataCallResponse response = new DataCallResponse();
            response.version = this.mPhone.mCi.getRilVersion();
            response.status = this.mDcTesterFailBringUpAll.getDcFailBringUp().mFailCause.getErrorCode();
            response.cid = 0;
            response.active = 0;
            response.type = "";
            response.ifname = "";
            response.addresses = new String[0];
            response.dnses = new String[0];
            response.gateways = new String[0];
            response.suggestedRetryTime = this.mDcTesterFailBringUpAll.getDcFailBringUp().mSuggestedRetryTime;
            response.pcscf = new String[0];
            response.mtu = 0;
            Message msg = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
            AsyncResult.forMessage(msg, response, (Throwable) null);
            sendMessage(msg);
            if (DBG) {
                log("onConnect: FailBringUpAll=" + this.mDcTesterFailBringUpAll.getDcFailBringUp() + " send error response=" + response);
            }
            DcFailBringUp dcFailBringUp = this.mDcTesterFailBringUpAll.getDcFailBringUp();
            dcFailBringUp.mCounter--;
            return;
        }
        this.mCreateTime = -1L;
        this.mLastFailTime = -1L;
        this.mLastFailCause = DcFailCause.NONE;
        if (this.mApnSetting.getApnProfileType() == ApnSetting.ApnProfileType.PROFILE_TYPE_OMH) {
            dataProfileId = this.mApnSetting.getProfileId() + 1000;
            log("OMH profile, dataProfile id = " + dataProfileId);
        } else {
            dataProfileId = cp.mProfileId;
        }
        Message msg2 = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
        msg2.obj = cp;
        int authType = this.mApnSetting.authType;
        if (authType == -1) {
            authType = TextUtils.isEmpty(this.mApnSetting.user) ? 0 : 3;
        }
        if (this.mPhone.getServiceState().getDataRoaming()) {
            protocol = this.mApnSetting.roamingProtocol;
        } else {
            protocol = this.mApnSetting.protocol;
        }
        this.mPhone.mCi.setupDataCall(Integer.toString(cp.mRilRat + 2), Integer.toString(dataProfileId), this.mApnSetting.apn, this.mApnSetting.user, this.mApnSetting.password, Integer.toString(authType), protocol, msg2);
    }

    public void tearDownData(Object o) {
        int discReason = 0;
        if (o != null && (o instanceof DisconnectParams)) {
            DisconnectParams dp = (DisconnectParams) o;
            if (TextUtils.equals(dp.mReason, Phone.REASON_RADIO_TURNED_OFF)) {
                discReason = 1;
            } else if (TextUtils.equals(dp.mReason, Phone.REASON_PDP_RESET)) {
                discReason = 2;
            }
        }
        if (this.mPhone.mCi.getRadioState().isOn() || this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            if (DBG) {
                log("tearDownData radio is on, call deactivateDataCall");
            }
            this.mPhone.mCi.deactivateDataCall(this.mCid, discReason, obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, o));
            return;
        }
        if (DBG) {
            log("tearDownData radio is off sendMessage EVENT_DEACTIVATE_DONE immediately");
        }
        sendMessage(obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, new AsyncResult(o, (Object) null, (Throwable) null)));
    }

    private void notifyAllWithEvent(ApnContext alreadySent, int event, String reason) {
        this.mNetworkInfo.setDetailedState(this.mNetworkInfo.getDetailedState(), reason, this.mNetworkInfo.getExtraInfo());
        for (ApnContext apnContext : this.mApnContexts) {
            if (apnContext != alreadySent) {
                if (reason != null) {
                    apnContext.setReason(reason);
                }
                Message msg = this.mDct.obtainMessage(event, apnContext);
                AsyncResult.forMessage(msg);
                msg.sendToTarget();
            }
        }
    }

    public void notifyAllOfConnected(String reason) {
        notifyAllWithEvent(null, 270336, reason);
    }

    public void notifyAllOfDisconnectDcRetrying(String reason) {
        notifyAllWithEvent(null, 270370, reason);
    }

    public void notifyAllDisconnectCompleted(DcFailCause cause) {
        notifyAllWithEvent(null, 270351, cause.toString());
    }

    public void notifyConnectCompleted(ConnectionParams cp, DcFailCause cause, boolean sendAll) {
        ApnContext alreadySent = null;
        if (!(cp == null || cp.mOnCompletedMsg == null)) {
            Message connectionCompletedMsg = cp.mOnCompletedMsg;
            cp.mOnCompletedMsg = null;
            if (connectionCompletedMsg.obj instanceof ApnContext) {
                alreadySent = (ApnContext) connectionCompletedMsg.obj;
            }
            long timeStamp = System.currentTimeMillis();
            connectionCompletedMsg.arg1 = this.mCid;
            if (cause == DcFailCause.NONE) {
                this.mCreateTime = timeStamp;
                AsyncResult.forMessage(connectionCompletedMsg);
            } else {
                this.mLastFailCause = cause;
                this.mLastFailTime = timeStamp;
                if (cause == null) {
                    cause = DcFailCause.UNKNOWN;
                }
                AsyncResult.forMessage(connectionCompletedMsg, cause, new Throwable(cause.toString()));
            }
            if (DBG) {
                log("notifyConnectCompleted at " + timeStamp + " cause=" + cause + " connectionCompletedMsg=" + msgToString(connectionCompletedMsg));
            }
            connectionCompletedMsg.sendToTarget();
        }
        if (sendAll) {
            notifyAllWithEvent(alreadySent, 270371, cause.toString());
        }
    }

    public void notifyDisconnectCompleted(DisconnectParams dp, boolean sendAll) {
        if (VDBG) {
            log("NotifyDisconnectCompleted");
        }
        ApnContext alreadySent = null;
        String reason = null;
        if (!(dp == null || dp.mOnCompletedMsg == null)) {
            Message msg = dp.mOnCompletedMsg;
            dp.mOnCompletedMsg = null;
            if (msg.obj instanceof ApnContext) {
                alreadySent = (ApnContext) msg.obj;
            }
            reason = dp.mReason;
            if (VDBG) {
                Object[] objArr = new Object[2];
                objArr[0] = msg.toString();
                objArr[1] = msg.obj instanceof String ? (String) msg.obj : "<no-reason>";
                log(String.format("msg=%s msg.obj=%s", objArr));
            }
            AsyncResult.forMessage(msg);
            msg.sendToTarget();
        }
        if (sendAll) {
            if (reason == null) {
                reason = DcFailCause.UNKNOWN.toString();
            }
            notifyAllWithEvent(alreadySent, 270351, reason);
        }
        if (DBG) {
            log("NotifyDisconnectCompleted DisconnectParams=" + dp);
        }
    }

    public int getDataConnectionId() {
        return this.mId;
    }

    public void clearSettings() {
        if (DBG) {
            log("clearSettings");
        }
        this.mCreateTime = -1L;
        this.mLastFailTime = -1L;
        this.mLastFailCause = DcFailCause.NONE;
        this.mCid = -1;
        this.mPcscfAddr = new String[5];
        this.mLinkProperties = new LinkProperties();
        this.mApnContexts.clear();
        this.mApnSetting = null;
        this.mDcFailCause = null;
    }

    public DataCallResponse.SetupResult onSetupConnectionCompleted(AsyncResult ar) {
        DataCallResponse response = (DataCallResponse) ar.result;
        ConnectionParams cp = (ConnectionParams) ar.userObj;
        if (cp.mTag != this.mTag) {
            if (DBG) {
                log("onSetupConnectionCompleted stale cp.tag=" + cp.mTag + ", mtag=" + this.mTag);
            }
            return DataCallResponse.SetupResult.ERR_Stale;
        } else if (ar.exception != null) {
            if (DBG) {
                log("onSetupConnectionCompleted failed, ar.exception=" + ar.exception + " response=" + response);
            }
            if ((ar.exception instanceof CommandException) && ((CommandException) ar.exception).getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE) {
                DataCallResponse.SetupResult result = DataCallResponse.SetupResult.ERR_BadCommand;
                result.mFailCause = DcFailCause.RADIO_NOT_AVAILABLE;
                return result;
            } else if (response == null || response.version < 4) {
                return DataCallResponse.SetupResult.ERR_GetLastErrorFromRil;
            } else {
                DataCallResponse.SetupResult result2 = DataCallResponse.SetupResult.ERR_RilError;
                result2.mFailCause = DcFailCause.fromInt(response.status);
                return result2;
            }
        } else if (response.status != 0) {
            DataCallResponse.SetupResult result3 = DataCallResponse.SetupResult.ERR_RilError;
            result3.mFailCause = DcFailCause.fromInt(response.status);
            return result3;
        } else {
            if (DBG) {
                log("onSetupConnectionCompleted received DataCallResponse: " + response);
            }
            this.mCid = response.cid;
            this.mPcscfAddr = response.pcscf;
            return updateLinkProperty(response).setupResult;
        }
    }

    private boolean isDnsOk(String[] domainNameServers) {
        if (!NULL_IP.equals(domainNameServers[0]) || !NULL_IP.equals(domainNameServers[1]) || this.mPhone.isDnsCheckDisabled() || (this.mApnSetting.types[0].equals("mms") && isIpAddress(this.mApnSetting.mmsProxy))) {
            return true;
        }
        log(String.format("isDnsOk: return false apn.types[0]=%s APN_TYPE_MMS=%s isIpAddress(%s)=%s", this.mApnSetting.types[0], "mms", this.mApnSetting.mmsProxy, Boolean.valueOf(isIpAddress(this.mApnSetting.mmsProxy))));
        return false;
    }

    public void updateTcpBufferSizes(int rilRat) {
        String sizes = null;
        String ratName = ServiceState.rilRadioTechnologyToString(rilRat).toLowerCase(Locale.ROOT);
        if (rilRat == 7 || rilRat == 8 || rilRat == 12) {
            ratName = "evdo";
        }
        String[] configOverride = this.mPhone.getContext().getResources().getStringArray(17236014);
        int i = 0;
        while (true) {
            if (i >= configOverride.length) {
                break;
            }
            String[] split = configOverride[i].split(":");
            if (ratName.equals(split[0]) && split.length == 2) {
                sizes = split[1];
                break;
            }
            i++;
        }
        if (sizes == null) {
            switch (rilRat) {
                case 1:
                    sizes = TCP_BUFFER_SIZES_GPRS;
                    break;
                case 2:
                    sizes = TCP_BUFFER_SIZES_EDGE;
                    break;
                case 3:
                    sizes = TCP_BUFFER_SIZES_UMTS;
                    break;
                case 6:
                    sizes = TCP_BUFFER_SIZES_1XRTT;
                    break;
                case 7:
                case 8:
                case 12:
                    sizes = TCP_BUFFER_SIZES_EVDO;
                    break;
                case 9:
                    sizes = TCP_BUFFER_SIZES_HSDPA;
                    break;
                case 10:
                case 11:
                    sizes = TCP_BUFFER_SIZES_HSPA;
                    break;
                case 13:
                    sizes = TCP_BUFFER_SIZES_EHRPD;
                    break;
                case 14:
                case 19:
                    sizes = TCP_BUFFER_SIZES_LTE;
                    break;
                case 15:
                    sizes = TCP_BUFFER_SIZES_HSPAP;
                    break;
            }
        }
        this.mLinkProperties.setTcpBufferSizes(sizes);
    }

    public NetworkCapabilities makeNetworkCapabilities() {
        NetworkCapabilities result = new NetworkCapabilities();
        result.addTransportType(0);
        if (this.mApnSetting != null) {
            String[] arr$ = this.mApnSetting.types;
            for (String type : arr$) {
                char c = 65535;
                switch (type.hashCode()) {
                    case 42:
                        if (type.equals(CharacterSets.MIMENAME_ANY_CHARSET)) {
                            c = 0;
                            break;
                        }
                        break;
                    case 3352:
                        if (type.equals("ia")) {
                            c = '\b';
                            break;
                        }
                        break;
                    case 98292:
                        if (type.equals("cbs")) {
                            c = 7;
                            break;
                        }
                        break;
                    case 99837:
                        if (type.equals("dun")) {
                            c = 4;
                            break;
                        }
                        break;
                    case 104399:
                        if (type.equals("ims")) {
                            c = 6;
                            break;
                        }
                        break;
                    case 108243:
                        if (type.equals("mms")) {
                            c = 2;
                            break;
                        }
                        break;
                    case 3149046:
                        if (type.equals("fota")) {
                            c = 5;
                            break;
                        }
                        break;
                    case 3541982:
                        if (type.equals("supl")) {
                            c = 3;
                            break;
                        }
                        break;
                    case 1544803905:
                        if (type.equals("default")) {
                            c = 1;
                            break;
                        }
                        break;
                }
                switch (c) {
                    case 0:
                        result.addCapability(12);
                        result.addCapability(0);
                        result.addCapability(1);
                        result.addCapability(3);
                        result.addCapability(4);
                        result.addCapability(5);
                        result.addCapability(7);
                        if (TelBrand.IS_KDDI) {
                            result.addCapability(2);
                            break;
                        }
                        break;
                    case 1:
                        result.addCapability(12);
                        break;
                    case 2:
                        result.addCapability(0);
                        break;
                    case 3:
                        result.addCapability(1);
                        break;
                    case 4:
                        ApnSetting securedDunApn = this.mDct.fetchDunApn();
                        if (securedDunApn == null || securedDunApn.equals(this.mApnSetting)) {
                            result.addCapability(2);
                            break;
                        }
                        break;
                    case 5:
                        result.addCapability(3);
                        break;
                    case 6:
                        result.addCapability(4);
                        break;
                    case 7:
                        result.addCapability(5);
                        break;
                    case '\b':
                        result.addCapability(7);
                        break;
                }
                if (this.mPhone.getSubId() != SubscriptionManager.getDefaultDataSubId()) {
                    log("DataConnection on non-dds does not have INTERNET capability.");
                    result.removeCapability(12);
                }
            }
            ConnectivityManager.maybeMarkCapabilitiesRestricted(result);
        }
        int up = 14;
        int down = 14;
        switch (this.mRilRat) {
            case 1:
                up = 80;
                down = 80;
                break;
            case 2:
                up = 59;
                down = 236;
                break;
            case 3:
                up = 384;
                down = 384;
                break;
            case 4:
            case 5:
                up = 14;
                down = 14;
                break;
            case 6:
                up = 100;
                down = 100;
                break;
            case 7:
                up = 153;
                down = 2457;
                break;
            case 8:
                up = 1843;
                down = 3174;
                break;
            case 9:
                up = 2048;
                down = 14336;
                break;
            case 10:
                up = 5898;
                down = 14336;
                break;
            case 11:
                up = 5898;
                down = 14336;
                break;
            case 12:
                up = 1843;
                down = 5017;
                break;
            case 13:
                up = 153;
                down = 2516;
                break;
            case 14:
                up = 51200;
                down = 102400;
                break;
            case 15:
                up = 11264;
                down = 43008;
                break;
            case 19:
                up = 51200;
                down = 102400;
                break;
        }
        result.setLinkUpstreamBandwidthKbps(up);
        result.setLinkDownstreamBandwidthKbps(down);
        result.setNetworkSpecifier("" + this.mPhone.getSubId());
        return result;
    }

    private boolean isIpAddress(String address) {
        if (address == null) {
            return false;
        }
        return Patterns.IP_ADDRESS.matcher(address).matches();
    }

    private DataCallResponse.SetupResult setLinkProperties(DataCallResponse response, LinkProperties lp) {
        String propertyPrefix = "net." + response.ifname + ".";
        return response.setLinkProperties(lp, isDnsOk(new String[]{SystemProperties.get(propertyPrefix + "dns1"), SystemProperties.get(propertyPrefix + "dns2")}));
    }

    public boolean initConnection(ConnectionParams cp) {
        ApnContext apnContext = cp.mApnContext;
        if (this.mApnSetting == null) {
            this.mApnSetting = apnContext.getApnSetting();
        }
        if (this.mApnSetting == null || !this.mApnSetting.canHandleType(apnContext.getApnType())) {
            if (DBG) {
                log("initConnection: incompatible apnSetting in ConnectionParams cp=" + cp + " dc=" + this);
            }
            return false;
        }
        this.mTag++;
        this.mConnectionParams = cp;
        this.mConnectionParams.mTag = this.mTag;
        if (!this.mApnContexts.contains(apnContext)) {
            this.mApnContexts.add(apnContext);
        }
        configureRetry(this.mApnSetting.canHandleType("default"));
        this.mRetryManager.setRetryCount(0);
        if (TelBrand.IS_DCM || TelBrand.IS_KDDI) {
            this.mRetryManager.setRetryForever(true);
        } else {
            this.mRetryManager.setCurMaxRetryCount(this.mConnectionParams.mInitialMaxRetry);
            this.mRetryManager.setRetryForever(false);
        }
        if (!DBG) {
            return true;
        }
        log("initConnection:  RefCount=" + this.mApnContexts.size() + " mApnList=" + this.mApnContexts + " mConnectionParams=" + this.mConnectionParams);
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcDefaultState extends State {
        private DcDefaultState() {
            DataConnection.this = r1;
        }

        public void enter() {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcDefaultState: enter");
            }
            DataConnection.this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED, null);
            DataConnection.this.mPhone.getServiceStateTracker().registerForDataRoamingOn(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_ROAM_ON, null);
            DataConnection.this.mPhone.getServiceStateTracker().registerForDataRoamingOff(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF, null);
            DataConnection.this.mDcController.addDc(DataConnection.this);
        }

        public void exit() {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcDefaultState: exit");
            }
            DataConnection.this.mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(DataConnection.this.getHandler());
            DataConnection.this.mPhone.getServiceStateTracker().unregisterForDataRoamingOn(DataConnection.this.getHandler());
            DataConnection.this.mPhone.getServiceStateTracker().unregisterForDataRoamingOff(DataConnection.this.getHandler());
            DataConnection.this.mDcController.removeDc(DataConnection.this);
            if (DataConnection.this.mAc != null) {
                DataConnection.this.mAc.disconnected();
                DataConnection.this.mAc = null;
            }
            DataConnection.this.mDcRetryAlarmController.dispose();
            DataConnection.this.mDcRetryAlarmController = null;
            DataConnection.this.mApnContexts = null;
            DataConnection.this.mReconnectIntent = null;
            DataConnection.this.mDct = null;
            DataConnection.this.mApnSetting = null;
            DataConnection.this.mPhone = null;
            DataConnection.this.mLinkProperties = null;
            DataConnection.this.mLastFailCause = null;
            DataConnection.this.mUserData = null;
            DataConnection.this.mDcController = null;
            DataConnection.this.mDcTesterFailBringUpAll = null;
        }

        public boolean processMessage(Message msg) {
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcDefault msg=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
            }
            switch (msg.what) {
                case 69633:
                    if (DataConnection.this.mAc == null) {
                        DataConnection.this.mAc = new AsyncChannel();
                        DataConnection.this.mAc.connected((Context) null, DataConnection.this.getHandler(), msg.replyTo);
                        if (DataConnection.VDBG) {
                            DataConnection.this.log("DcDefaultState: FULL_CONNECTION reply connected");
                        }
                        DataConnection.this.mAc.replyToMessage(msg, 69634, 0, DataConnection.this.mId, "hi");
                        break;
                    } else {
                        if (DataConnection.VDBG) {
                            DataConnection.this.log("Disconnecting to previous connection mAc=" + DataConnection.this.mAc);
                        }
                        DataConnection.this.mAc.replyToMessage(msg, 69634, 3);
                        break;
                    }
                case 69636:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("CMD_CHANNEL_DISCONNECTED");
                    }
                    DataConnection.this.quit();
                    break;
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: msg.what=EVENT_CONNECT, fail not expected");
                    }
                    DataConnection.this.notifyConnectCompleted((ConnectionParams) msg.obj, DcFailCause.UNKNOWN, false);
                    break;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(msg);
                    break;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(msg);
                    break;
                case DataConnection.EVENT_TEAR_DOWN_NOW /* 262152 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState EVENT_TEAR_DOWN_NOW");
                    }
                    DataConnection.this.mPhone.mCi.deactivateDataCall(DataConnection.this.mCid, 0, null);
                    break;
                case DataConnection.EVENT_LOST_CONNECTION /* 262153 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_LOST_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag);
                        break;
                    }
                    break;
                case DataConnection.EVENT_RETRY_CONNECTION /* 262154 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_RETRY_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag);
                        break;
                    }
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    Pair<Integer, Integer> drsRatPair = (Pair) ((AsyncResult) msg.obj).result;
                    DataConnection.this.mDataRegState = ((Integer) drsRatPair.first).intValue();
                    if (DataConnection.this.mRilRat != ((Integer) drsRatPair.second).intValue()) {
                        DataConnection.this.updateTcpBufferSizes(((Integer) drsRatPair.second).intValue());
                    }
                    DataConnection.this.mRilRat = ((Integer) drsRatPair.second).intValue();
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED drs=" + DataConnection.this.mDataRegState + " mRilRat=" + DataConnection.this.mRilRat);
                    }
                    int networkType = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                    DataConnection.this.mNetworkInfo.setSubtype(networkType, TelephonyManager.getNetworkTypeName(networkType));
                    if (DataConnection.this.mNetworkAgent != null) {
                        DataConnection.this.mNetworkAgent.sendNetworkCapabilities(DataConnection.this.makeNetworkCapabilities());
                        DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                        DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                        break;
                    }
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /* 262156 */:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /* 262157 */:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    break;
                case 266240:
                    boolean val = DataConnection.this.getIsInactive();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_IS_INACTIVE  isInactive=" + val);
                    }
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_IS_INACTIVE, val ? 1 : 0);
                    break;
                case DcAsyncChannel.REQ_GET_CID /* 266242 */:
                    int cid = DataConnection.this.getCid();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_CID  cid=" + cid);
                    }
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_GET_CID, cid);
                    break;
                case DcAsyncChannel.REQ_GET_APNSETTING /* 266244 */:
                    ApnSetting apnSetting = DataConnection.this.getApnSetting();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_APNSETTING  mApnSetting=" + apnSetting);
                    }
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_GET_APNSETTING, apnSetting);
                    break;
                case DcAsyncChannel.REQ_GET_LINK_PROPERTIES /* 266246 */:
                    LinkProperties lp = DataConnection.this.getCopyLinkProperties();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_LINK_PROPERTIES linkProperties" + lp);
                    }
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_GET_LINK_PROPERTIES, lp);
                    break;
                case DcAsyncChannel.REQ_SET_LINK_PROPERTIES_HTTP_PROXY /* 266248 */:
                    ProxyInfo proxy = (ProxyInfo) msg.obj;
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_SET_LINK_PROPERTIES_HTTP_PROXY proxy=" + proxy);
                    }
                    DataConnection.this.setLinkPropertiesHttpProxy(proxy);
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_SET_LINK_PROPERTIES_HTTP_PROXY);
                    if (DataConnection.this.mNetworkAgent != null) {
                        DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                        break;
                    }
                    break;
                case DcAsyncChannel.REQ_GET_NETWORK_CAPABILITIES /* 266250 */:
                    NetworkCapabilities nc = DataConnection.this.getCopyNetworkCapabilities();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_NETWORK_CAPABILITIES networkCapabilities" + nc);
                    }
                    DataConnection.this.mAc.replyToMessage(msg, (int) DcAsyncChannel.RSP_GET_NETWORK_CAPABILITIES, nc);
                    break;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDefaultState: msg.what=REQ_RESET");
                    }
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    break;
                default:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: shouldn't happen but ignore msg.what=" + DataConnection.this.getWhatToString(msg.what));
                        break;
                    }
                    break;
            }
            return true;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcInactiveState extends State {
        private DcInactiveState() {
            DataConnection.this = r1;
        }

        public void setEnterNotificationParams(ConnectionParams cp, DcFailCause cause) {
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcInactiveState: setEnterNoticationParams cp,cause");
            }
            DataConnection.this.mConnectionParams = cp;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void setEnterNotificationParams(DisconnectParams dp) {
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcInactiveState: setEnterNoticationParams dp");
            }
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = dp;
            DataConnection.this.mDcFailCause = DcFailCause.NONE;
        }

        public void setEnterNotificationParams(DcFailCause cause) {
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void enter() {
            DataConnection.this.mTag++;
            if (DataConnection.DBG) {
                DataConnection.this.log("DcInactiveState: enter() mTag=" + DataConnection.this.mTag);
            }
            if (DataConnection.this.mConnectionParams != null) {
                if (DataConnection.DBG) {
                    DataConnection.this.log("DcInactiveState: enter notifyConnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                }
                DataConnection.this.notifyConnectCompleted(DataConnection.this.mConnectionParams, DataConnection.this.mDcFailCause, true);
            }
            if (DataConnection.this.mDisconnectParams != null) {
                if (DataConnection.DBG) {
                    DataConnection.this.log("DcInactiveState: enter notifyDisconnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                }
                DataConnection.this.notifyDisconnectCompleted(DataConnection.this.mDisconnectParams, true);
            }
            if (DataConnection.this.mDisconnectParams == null && DataConnection.this.mConnectionParams == null && DataConnection.this.mDcFailCause != null) {
                if (DataConnection.DBG) {
                    DataConnection.this.log("DcInactiveState: enter notifyAllDisconnectCompleted failCause=" + DataConnection.this.mDcFailCause);
                }
                DataConnection.this.notifyAllDisconnectCompleted(DataConnection.this.mDcFailCause);
            }
            DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
            DataConnection.this.clearSettings();
        }

        public void exit() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: mag.what=EVENT_CONNECT");
                    }
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcInactiveState: msg.what=EVENT_CONNECT initConnection failed");
                        }
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT");
                    }
                    if (!TelBrand.IS_KDDI) {
                        DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT_ALL");
                    }
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    return true;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=RSP_RESET, ignore we're already reset");
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcInactiveState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    }
                    return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcRetryingState extends State {
        private DcRetryingState() {
            DataConnection.this = r1;
        }

        public void enter() {
            if (DataConnection.this.mConnectionParams.mRilRat == DataConnection.this.mRilRat && DataConnection.this.mDataRegState == 0) {
                if (DataConnection.DBG) {
                    DataConnection.this.log("DcRetryingState: enter() mTag=" + DataConnection.this.mTag + ", call notifyAllOfDisconnectDcRetrying lostConnection");
                }
                DataConnection.this.notifyAllOfDisconnectDcRetrying(Phone.REASON_LOST_DATA_CONNECTION);
                DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
                DataConnection.this.mCid = -1;
                return;
            }
            if (DataConnection.DBG) {
                DataConnection.this.logAndAddLogRec("DcRetryingState: enter() not retrying rat changed, mConnectionParams.mRilRat=" + DataConnection.this.mConnectionParams.mRilRat + " != mRilRat:" + DataConnection.this.mRilRat + " transitionTo(mInactiveState)");
            }
            DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size() + " cp=" + cp + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                    }
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT initConnection failed");
                        }
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DisconnectParams dp = (DisconnectParams) msg.obj;
                    if (!DataConnection.this.mApnContexts.remove(dp.mApnContext) || DataConnection.this.mApnContexts.size() != 0) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: msg.what=EVENT_DISCONNECT");
                        }
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT  RefCount=" + DataConnection.this.mApnContexts.size() + " dp=" + dp);
                        }
                        DataConnection.this.mInactiveState.setEnterNotificationParams(dp);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT/DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                case DataConnection.EVENT_RETRY_CONNECTION /* 262154 */:
                    if (msg.arg1 == DataConnection.this.mTag) {
                        DataConnection.this.mRetryManager.increaseRetryCount();
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState EVENT_RETRY_CONNECTION RetryCount=" + DataConnection.this.mRetryManager.getRetryCount() + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                        }
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState stale EVENT_RETRY_CONNECTION tag:" + msg.arg1 + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    Pair<Integer, Integer> drsRatPair = (Pair) ((AsyncResult) msg.obj).result;
                    int drs = ((Integer) drsRatPair.first).intValue();
                    int rat = ((Integer) drsRatPair.second).intValue();
                    if (rat == DataConnection.this.mRilRat && drs == DataConnection.this.mDataRegState) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED strange no change in drs=" + drs + " rat=" + rat + " ignoring");
                        }
                    } else if (DataConnection.this.mConnectionParams.mRetryWhenSSChange) {
                        return false;
                    } else {
                        if (drs != 0) {
                            DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                            DataConnection.this.deferMessage(msg);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            if (DataConnection.DBG) {
                                DataConnection.this.logAndAddLogRec("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED giving up changed from " + DataConnection.this.mRilRat + " to rat=" + rat + " or drs changed from " + DataConnection.this.mDataRegState + " to drs=" + drs);
                            }
                        }
                        DataConnection.this.mDataRegState = drs;
                        DataConnection.this.mRilRat = rat;
                        int networkType = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                        DataConnection.this.mNetworkInfo.setSubtype(networkType, TelephonyManager.getNetworkTypeName(networkType));
                    }
                    return true;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState: msg.what=RSP_RESET, ignore we're already reset");
                    }
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DataConnection.this.mConnectionParams, DcFailCause.RESET_BY_FRAMEWORK);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcRetryingState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    }
                    return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcActivatingState extends State {
        private DcActivatingState() {
            DataConnection.this = r1;
        }

        public boolean processMessage(Message msg) {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcActivatingState: msg=" + DataConnection.msgToString(msg));
            }
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /* 262145 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    ConnectionParams cp = (ConnectionParams) ar.userObj;
                    DataCallResponse.SetupResult result = DataConnection.this.onSetupConnectionCompleted(ar);
                    if (!(result == DataCallResponse.SetupResult.ERR_Stale || DataConnection.this.mConnectionParams == cp)) {
                        DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp);
                    }
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActivatingState onSetupConnectionCompleted result=" + result + " dc=" + DataConnection.this);
                    }
                    switch (result) {
                        case SUCCESS:
                            DataConnection.this.mDcFailCause = DcFailCause.NONE;
                            DataConnection.this.transitionTo(DataConnection.this.mActiveState);
                            break;
                        case ERR_BadCommand:
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            break;
                        case ERR_UnacceptableParameter:
                            DataConnection.this.tearDownData(cp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingErrorCreatingConnection);
                            break;
                        case ERR_GetLastErrorFromRil:
                            DataConnection.this.mPhone.mCi.getLastDataCallFailCause(DataConnection.this.obtainMessage(DataConnection.EVENT_GET_LAST_FAIL_DONE, cp));
                            break;
                        case ERR_RilError:
                            if (TelBrand.IS_KDDI) {
                                DataConnection.this.mDct.sendOemKddiFailCauseBroadcast(result.mFailCause, (ApnContext) cp.mOnCompletedMsg.obj);
                            }
                            int delay = DataConnection.this.mDcRetryAlarmController.getSuggestedRetryTime(DataConnection.this, ar);
                            if (DataConnection.DBG) {
                                DataConnection.this.log("DcActivatingState: ERR_RilError  delay=" + delay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " result=" + result + " result.isRestartRadioFail=" + result.mFailCause.isRestartRadioFail() + " result.isPermanentFail=" + DataConnection.this.mDct.isPermanentFail(result.mFailCause));
                            }
                            if (!result.mFailCause.isRestartRadioFail()) {
                                if (!DataConnection.this.mDct.isPermanentFail(result.mFailCause)) {
                                    if (delay < 0) {
                                        if (DataConnection.DBG) {
                                            DataConnection.this.log("DcActivatingState: ERR_RilError no retry");
                                        }
                                        DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                                        break;
                                    } else {
                                        if (DataConnection.DBG) {
                                            DataConnection.this.log("DcActivatingState: ERR_RilError retry");
                                        }
                                        DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delay);
                                        DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                                        break;
                                    }
                                } else {
                                    if (DataConnection.DBG) {
                                        DataConnection.this.log("DcActivatingState: ERR_RilError perm error");
                                    }
                                    DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                                    break;
                                }
                            } else {
                                if (DataConnection.DBG) {
                                    DataConnection.this.log("DcActivatingState: ERR_RilError restart radio");
                                }
                                DataConnection.this.mDct.sendRestartRadio();
                                DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                                break;
                            }
                        case ERR_Stale:
                            DataConnection.this.loge("DcActivatingState: stale EVENT_SETUP_DATA_CONNECTION_DONE tag:" + cp.mTag + " != mTag:" + DataConnection.this.mTag);
                            break;
                        default:
                            throw new RuntimeException("Unknown SetupResult, should not happen");
                    }
                    return true;
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /* 262146 */:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    ConnectionParams cp2 = (ConnectionParams) ar2.userObj;
                    if (cp2.mTag == DataConnection.this.mTag) {
                        if (DataConnection.this.mConnectionParams != cp2) {
                            DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp2);
                        }
                        DcFailCause cause = DcFailCause.UNKNOWN;
                        if (ar2.exception == null) {
                            cause = DcFailCause.fromInt(((int[]) ar2.result)[0]);
                            if (TelBrand.IS_KDDI) {
                                DataConnection.this.mDct.sendOemKddiFailCauseBroadcast(cause, (ApnContext) cp2.mOnCompletedMsg.obj);
                            }
                            if (cause == DcFailCause.NONE) {
                                if (DataConnection.DBG) {
                                    DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE BAD: error was NONE, change to UNKNOWN");
                                }
                                cause = DcFailCause.UNKNOWN;
                            }
                        }
                        DataConnection.this.mDcFailCause = cause;
                        int retryDelay = DataConnection.this.mRetryManager.getRetryTimer();
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE cause=" + cause + " retryDelay=" + retryDelay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " dc=" + DataConnection.this);
                        }
                        if (cause.isRestartRadioFail()) {
                            if (DataConnection.DBG) {
                                DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE restart radio");
                            }
                            DataConnection.this.mDct.sendRestartRadio();
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (DataConnection.this.mDct.isPermanentFail(cause)) {
                            if (DataConnection.DBG) {
                                DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE perm er");
                            }
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (retryDelay < 0 || !DataConnection.this.mRetryManager.isRetryNeeded()) {
                            if (DataConnection.DBG) {
                                DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE no retry");
                            }
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else {
                            if (DataConnection.DBG) {
                                DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE retry");
                            }
                            DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, retryDelay);
                            DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                        }
                    } else {
                        DataConnection.this.loge("DcActivatingState: stale EVENT_GET_LAST_FAIL_DONE tag:" + cp2.mTag + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcActivatingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcActiveState extends State {
        private DcActiveState() {
            DataConnection.this = r1;
        }

        public void enter() {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcActiveState: enter dc=" + DataConnection.this);
            }
            if (DataConnection.this.mRetryManager.getRetryCount() != 0) {
                DataConnection.this.log("DcActiveState: connected after retrying call notifyAllOfConnected");
                DataConnection.this.mRetryManager.setRetryCount(0);
            }
            DataConnection.this.notifyAllOfConnected(Phone.REASON_CONNECTED);
            DataConnection.this.mRetryManager.restoreCurMaxRetryCount();
            DataConnection.this.mDcController.addActiveDcByCid(DataConnection.this);
            DataConnection.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, DataConnection.this.mNetworkInfo.getReason(), null);
            DataConnection.this.mNetworkInfo.setExtraInfo(DataConnection.this.mApnSetting.apn);
            DataConnection.this.updateTcpBufferSizes(DataConnection.this.mRilRat);
            NetworkMisc misc = new NetworkMisc();
            misc.subscriberId = DataConnection.this.mPhone.getSubscriberId();
            DataConnection.this.mNetworkAgent = new DcNetworkAgent(DataConnection.this.getHandler().getLooper(), DataConnection.this.mPhone.getContext(), "DcNetworkAgent" + DataConnection.this.mPhone.getSubId(), DataConnection.this.mNetworkInfo, DataConnection.this.makeNetworkCapabilities(), DataConnection.this.mLinkProperties, 50, misc);
        }

        public void exit() {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcActiveState: exit dc=" + this);
            }
            DataConnection.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, DataConnection.this.mNetworkInfo.getReason(), DataConnection.this.mNetworkInfo.getExtraInfo());
            DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
            DataConnection.this.mNetworkAgent = null;
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState: EVENT_CONNECT cp=" + cp + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(cp.mApnContext)) {
                        DataConnection.this.log("DcActiveState ERROR already added apnContext=" + cp.mApnContext);
                    } else {
                        DataConnection.this.mApnContexts.add(cp.mApnContext);
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        }
                    }
                    DataConnection.this.notifyConnectCompleted(cp, DcFailCause.NONE, false);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /* 262145 */:
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /* 262146 */:
                case DataConnection.EVENT_DEACTIVATE_DONE /* 262147 */:
                case DataConnection.EVENT_RIL_CONNECTED /* 262149 */:
                case DataConnection.EVENT_DATA_STATE_CHANGED /* 262151 */:
                case DataConnection.EVENT_TEAR_DOWN_NOW /* 262152 */:
                case DataConnection.EVENT_RETRY_CONNECTION /* 262154 */:
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcActiveState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    }
                    return false;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DisconnectParams dp = (DisconnectParams) msg.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState: EVENT_DISCONNECT dp=" + dp + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(dp.mApnContext)) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        }
                        if (DataConnection.this.mApnContexts.size() == 1) {
                            DataConnection.this.mApnContexts.clear();
                            DataConnection.this.mDisconnectParams = dp;
                            DataConnection.this.mConnectionParams = null;
                            dp.mTag = DataConnection.this.mTag;
                            DataConnection.this.tearDownData(dp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                        } else {
                            DataConnection.this.mApnContexts.remove(dp.mApnContext);
                            DataConnection.this.notifyDisconnectCompleted(dp, false);
                        }
                    } else {
                        DataConnection.this.log("DcActiveState ERROR no such apnContext=" + dp.mApnContext + " in this dc=" + DataConnection.this);
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState EVENT_DISCONNECT clearing apn contexts, dc=" + DataConnection.this);
                    }
                    DisconnectParams dp2 = (DisconnectParams) msg.obj;
                    DataConnection.this.mDisconnectParams = dp2;
                    DataConnection.this.mConnectionParams = null;
                    dp2.mTag = DataConnection.this.mTag;
                    DataConnection.this.tearDownData(dp2);
                    DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                    return true;
                case DataConnection.EVENT_LOST_CONNECTION /* 262153 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mRetryManager.isRetryNeeded()) {
                        int delayMillis = DataConnection.this.mRetryManager.getRetryTimer();
                        if (TelBrand.IS_KDDI) {
                            delayMillis = 1000;
                        }
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION startRetryAlarm mTag=" + DataConnection.this.mTag + " delay=" + delayMillis + "ms");
                        }
                        DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delayMillis);
                        DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                    } else {
                        DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /* 262156 */:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /* 262157 */:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcDisconnectingState extends State {
        private DcDisconnectingState() {
            DataConnection.this = r1;
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectingState msg.what=EVENT_CONNECT. Defer. RefCount = " + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /* 262145 */:
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /* 262146 */:
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDisconnectingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    }
                    return false;
                case DataConnection.EVENT_DEACTIVATE_DONE /* 262147 */:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectingState msg.what=EVENT_DEACTIVATE_DONE RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    AsyncResult ar = (AsyncResult) msg.obj;
                    DisconnectParams dp = (DisconnectParams) ar.userObj;
                    if (dp.mTag == DataConnection.this.mTag) {
                        DataConnection.this.mInactiveState.setEnterNotificationParams((DisconnectParams) ar.userObj);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectState stale EVENT_DEACTIVATE_DONE dp.tag=" + dp.mTag + " mTag=" + DataConnection.this.mTag);
                    }
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DcDisconnectionErrorCreatingConnection extends State {
        private DcDisconnectionErrorCreatingConnection() {
            DataConnection.this = r1;
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case DataConnection.EVENT_DEACTIVATE_DONE /* 262147 */:
                    ConnectionParams cp = (ConnectionParams) ((AsyncResult) msg.obj).userObj;
                    if (cp.mTag == DataConnection.this.mTag) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcDisconnectionErrorCreatingConnection msg.what=EVENT_DEACTIVATE_DONE");
                        }
                        DataConnection.this.mInactiveState.setEnterNotificationParams(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection stale EVENT_DEACTIVATE_DONE dp.tag=" + cp.mTag + ", mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    }
                    return false;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class DcNetworkAgent extends NetworkAgent {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public DcNetworkAgent(Looper l, Context c, String TAG, NetworkInfo ni, NetworkCapabilities nc, LinkProperties lp, int score, NetworkMisc misc) {
            super(l, c, TAG, ni, nc, lp, score, misc);
            DataConnection.this = r10;
        }

        protected void unwanted() {
            if (DataConnection.this.mNetworkAgent != this) {
                log("unwanted found mNetworkAgent=" + DataConnection.this.mNetworkAgent + ", which isn't me.  Aborting unwanted");
            } else if (DataConnection.this.mApnContexts != null) {
                for (ApnContext apnContext : DataConnection.this.mApnContexts) {
                    DataConnection.this.sendMessage(DataConnection.this.obtainMessage(DataConnection.EVENT_DISCONNECT, new DisconnectParams(apnContext, apnContext.getReason(), DataConnection.this.mDct.obtainMessage(270351, apnContext))));
                }
            }
        }
    }

    public void tearDownNow() {
        if (DBG) {
            log("tearDownNow()");
        }
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_NOW));
    }

    public String getWhatToString(int what) {
        return cmdToString(what);
    }

    public static String msgToString(Message msg) {
        if (msg == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder();
        b.append("{what=");
        b.append(cmdToString(msg.what));
        b.append(" when=");
        TimeUtils.formatDuration(msg.getWhen() - SystemClock.uptimeMillis(), b);
        if (msg.arg1 != 0) {
            b.append(" arg1=");
            b.append(msg.arg1);
        }
        if (msg.arg2 != 0) {
            b.append(" arg2=");
            b.append(msg.arg2);
        }
        if (msg.obj != null) {
            b.append(" obj=");
            b.append(msg.obj);
        }
        b.append(" target=");
        b.append(msg.getTarget());
        b.append(" replyTo=");
        b.append(msg.replyTo);
        b.append("}");
        return b.toString();
    }

    static void slog(String s) {
        Rlog.d("DC", s);
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void logd(String s) {
        Rlog.d(getName(), s);
    }

    protected void logv(String s) {
        Rlog.v(getName(), s);
    }

    protected void logi(String s) {
        Rlog.i(getName(), s);
    }

    protected void logw(String s) {
        Rlog.w(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    public String toStringSimple() {
        return getName() + ": State=" + getCurrentState().getName() + " mApnSetting=" + this.mApnSetting + " RefCount=" + this.mApnContexts.size() + " mCid=" + this.mCid + " mCreateTime=" + this.mCreateTime + " mLastastFailTime=" + this.mLastFailTime + " mLastFailCause=" + this.mLastFailCause + " mTag=" + this.mTag + " mRetryManager=" + this.mRetryManager + " mLinkProperties=" + this.mLinkProperties + " linkCapabilities=" + makeNetworkCapabilities();
    }

    public String toString() {
        return "{" + toStringSimple() + " mApnContexts=" + this.mApnContexts + "}";
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("DataConnection ");
        DataConnection.super.dump(fd, pw, args);
        pw.println(" mApnContexts.size=" + this.mApnContexts.size());
        pw.println(" mApnContexts=" + this.mApnContexts);
        pw.flush();
        pw.println(" mDataConnectionTracker=" + this.mDct);
        pw.println(" mApnSetting=" + this.mApnSetting);
        pw.println(" mTag=" + this.mTag);
        pw.println(" mCid=" + this.mCid);
        pw.println(" mRetryManager=" + this.mRetryManager);
        pw.println(" mConnectionParams=" + this.mConnectionParams);
        pw.println(" mDisconnectParams=" + this.mDisconnectParams);
        pw.println(" mDcFailCause=" + this.mDcFailCause);
        pw.flush();
        pw.println(" mPhone=" + this.mPhone);
        pw.flush();
        pw.println(" mLinkProperties=" + this.mLinkProperties);
        pw.flush();
        pw.println(" mDataRegState=" + this.mDataRegState);
        pw.println(" mRilRat=" + this.mRilRat);
        pw.println(" mNetworkCapabilities=" + makeNetworkCapabilities());
        pw.println(" mCreateTime=" + TimeUtils.logTimeOfDay(this.mCreateTime));
        pw.println(" mLastFailTime=" + TimeUtils.logTimeOfDay(this.mLastFailTime));
        pw.println(" mLastFailCause=" + this.mLastFailCause);
        pw.flush();
        pw.println(" mUserData=" + this.mUserData);
        pw.println(" mInstanceNumber=" + mInstanceNumber);
        pw.println(" mAc=" + this.mAc);
        pw.println(" mDcRetryAlarmController=" + this.mDcRetryAlarmController);
        pw.flush();
    }
}
