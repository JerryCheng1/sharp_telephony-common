package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
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
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.dataconnection.ApnSetting.ApnProfileType;
import com.android.internal.telephony.dataconnection.DataCallResponse.SetupResult;
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

public final class DataConnection extends StateMachine {
    static final int BASE = 262144;
    private static final int CMD_TO_STRING_COUNT = 14;
    private static boolean DBG = true;
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
    private static boolean VDBG = true;
    private static AtomicInteger mInstanceNumber = new AtomicInteger(0);
    private static String[] sCmdToString = new String[14];
    private AsyncChannel mAc;
    private DcActivatingState mActivatingState = new DcActivatingState();
    private DcActiveState mActiveState = new DcActiveState();
    List<ApnContext> mApnContexts = null;
    private ApnSetting mApnSetting;
    int mCid;
    private ConnectionParams mConnectionParams;
    private long mCreateTime;
    private int mDataRegState = Integer.MAX_VALUE;
    private DcController mDcController;
    private DcFailCause mDcFailCause;
    private DcRetryAlarmController mDcRetryAlarmController;
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcTrackerBase mDct = null;
    private DcDefaultState mDefaultState = new DcDefaultState();
    private DisconnectParams mDisconnectParams;
    private DcDisconnectionErrorCreatingConnection mDisconnectingErrorCreatingConnection = new DcDisconnectionErrorCreatingConnection();
    private DcDisconnectingState mDisconnectingState = new DcDisconnectingState();
    private int mId;
    private DcInactiveState mInactiveState = new DcInactiveState();
    private DcFailCause mLastFailCause;
    private long mLastFailTime;
    private LinkProperties mLinkProperties = new LinkProperties();
    private NetworkAgent mNetworkAgent;
    private NetworkInfo mNetworkInfo;
    protected String[] mPcscfAddr;
    private PhoneBase mPhone;
    PendingIntent mReconnectIntent = null;
    RetryManager mRetryManager = new RetryManager();
    private DcRetryingState mRetryingState = new DcRetryingState();
    private int mRilRat = Integer.MAX_VALUE;
    int mTag;
    private Object mUserData;

    static class ConnectionParams {
        ApnContext mApnContext;
        int mInitialMaxRetry;
        Message mOnCompletedMsg;
        int mProfileId;
        boolean mRetryWhenSSChange;
        int mRilRat;
        int mTag;

        ConnectionParams(ApnContext apnContext, int i, int i2, int i3, boolean z, Message message) {
            this.mApnContext = apnContext;
            this.mInitialMaxRetry = i;
            this.mProfileId = i2;
            this.mRilRat = i3;
            this.mRetryWhenSSChange = z;
            this.mOnCompletedMsg = message;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mInitialMaxRetry=" + this.mInitialMaxRetry + " mProfileId=" + this.mProfileId + " mRat=" + this.mRilRat + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    private class DcActivatingState extends State {
        private DcActivatingState() {
        }

        /* JADX WARNING: Removed duplicated region for block: B:73:0x032f  */
        /* JADX WARNING: Removed duplicated region for block: B:81:0x03a2  */
        /* JADX WARNING: Removed duplicated region for block: B:76:0x0375  */
        public boolean processMessage(android.os.Message r10) {
            /*
            r9 = this;
            r8 = 262154; // 0x4000a float:3.67356E-40 double:1.295213E-318;
            r3 = 0;
            r4 = 1;
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x0027;
        L_0x000b:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = new java.lang.StringBuilder;
            r1.<init>();
            r2 = "DcActivatingState: msg=";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.msgToString(r10);
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.log(r1);
        L_0x0027:
            r0 = r10.what;
            switch(r0) {
                case 262144: goto L_0x0066;
                case 262145: goto L_0x006d;
                case 262146: goto L_0x02a2;
                case 262155: goto L_0x0066;
                default: goto L_0x002c;
            };
        L_0x002c:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.VDBG;
            if (r0 == 0) goto L_0x0064;
        L_0x0032:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = new java.lang.StringBuilder;
            r1.<init>();
            r2 = "DcActivatingState not handled msg.what=";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r4 = r10.what;
            r2 = r2.getWhatToString(r4);
            r1 = r1.append(r2);
            r2 = " RefCount=";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mApnContexts;
            r2 = r2.size();
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.log(r1);
        L_0x0064:
            r0 = r3;
        L_0x0065:
            return r0;
        L_0x0066:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0.deferMessage(r10);
            r0 = r4;
            goto L_0x0065;
        L_0x006d:
            r0 = r10.obj;
            r0 = (android.os.AsyncResult) r0;
            r1 = r0.userObj;
            r1 = (com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams) r1;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r2.onSetupConnectionCompleted(r0);
            r2 = com.android.internal.telephony.dataconnection.DataCallResponse.SetupResult.ERR_Stale;
            if (r3 == r2) goto L_0x00af;
        L_0x007f:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mConnectionParams;
            if (r2 == r1) goto L_0x00af;
        L_0x0087:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "DcActivatingState: WEIRD mConnectionsParams:";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.mConnectionParams;
            r5 = r5.append(r6);
            r6 = " != cp:";
            r5 = r5.append(r6);
            r5 = r5.append(r1);
            r5 = r5.toString();
            r2.loge(r5);
        L_0x00af:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r2 == 0) goto L_0x00d9;
        L_0x00b5:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "DcActivatingState onSetupConnectionCompleted result=";
            r5 = r5.append(r6);
            r5 = r5.append(r3);
            r6 = " dc=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = r5.append(r6);
            r5 = r5.toString();
            r2.log(r5);
        L_0x00d9:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.AnonymousClass1.$SwitchMap$com$android$internal$telephony$dataconnection$DataCallResponse$SetupResult;
            r5 = r3.ordinal();
            r2 = r2[r5];
            switch(r2) {
                case 1: goto L_0x00ec;
                case 2: goto L_0x0101;
                case 3: goto L_0x0118;
                case 4: goto L_0x0129;
                case 5: goto L_0x013e;
                case 6: goto L_0x0278;
                default: goto L_0x00e4;
            };
        L_0x00e4:
            r0 = new java.lang.RuntimeException;
            r1 = "Unknown SetupResult, should not happen";
            r0.<init>(r1);
            throw r0;
        L_0x00ec:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DcFailCause.NONE;
            r0.mDcFailCause = r1;
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mActiveState;
            r0.transitionTo(r1);
        L_0x00fe:
            r0 = r4;
            goto L_0x0065;
        L_0x0101:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mInactiveState;
            r2 = r3.mFailCause;
            r0.setEnterNotificationParams(r1, r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x0118:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0.tearDownData(r1);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mDisconnectingErrorCreatingConnection;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x0129:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mPhone;
            r0 = r0.mCi;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = 262146; // 0x40002 float:3.67345E-40 double:1.295173E-318;
            r1 = r2.obtainMessage(r3, r1);
            r0.getLastDataCallFailCause(r1);
            goto L_0x00fe;
        L_0x013e:
            r2 = com.android.internal.telephony.TelBrand.IS_KDDI;
            if (r2 == 0) goto L_0x0155;
        L_0x0142:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = r2.mDct;
            r6 = r3.mFailCause;
            r2 = r1.mOnCompletedMsg;
            r2 = r2.obj;
            r2 = (com.android.internal.telephony.dataconnection.ApnContext) r2;
            r2 = (com.android.internal.telephony.dataconnection.ApnContext) r2;
            r5.sendOemKddiFailCauseBroadcast(r6, r2);
        L_0x0155:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mDcRetryAlarmController;
            r5 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r2.getSuggestedRetryTime(r5, r0);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r2 == 0) goto L_0x01c1;
        L_0x0167:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "DcActivatingState: ERR_RilError  delay=";
            r5 = r5.append(r6);
            r5 = r5.append(r0);
            r6 = " isRetryNeeded=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.mRetryManager;
            r6 = r6.isRetryNeeded();
            r5 = r5.append(r6);
            r6 = " result=";
            r5 = r5.append(r6);
            r5 = r5.append(r3);
            r6 = " result.isRestartRadioFail=";
            r5 = r5.append(r6);
            r6 = r3.mFailCause;
            r6 = r6.isRestartRadioFail();
            r5 = r5.append(r6);
            r6 = " result.isPermanentFail=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.mDct;
            r7 = r3.mFailCause;
            r6 = r6.isPermanentFail(r7);
            r5 = r5.append(r6);
            r5 = r5.toString();
            r2.log(r5);
        L_0x01c1:
            r2 = r3.mFailCause;
            r2 = r2.isRestartRadioFail();
            if (r2 == 0) goto L_0x01f7;
        L_0x01c9:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x01d6;
        L_0x01cf:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActivatingState: ERR_RilError restart radio";
            r0.log(r2);
        L_0x01d6:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mDct;
            r0.sendRestartRadio();
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mInactiveState;
            r2 = r3.mFailCause;
            r0.setEnterNotificationParams(r1, r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x01f7:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mDct;
            r5 = r3.mFailCause;
            r2 = r2.isPermanentFail(r5);
            if (r2 == 0) goto L_0x022a;
        L_0x0205:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x0212;
        L_0x020b:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActivatingState: ERR_RilError perm error";
            r0.log(r2);
        L_0x0212:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mInactiveState;
            r2 = r3.mFailCause;
            r0.setEnterNotificationParams(r1, r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x022a:
            if (r0 < 0) goto L_0x0253;
        L_0x022c:
            r1 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r1 == 0) goto L_0x0239;
        L_0x0232:
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActivatingState: ERR_RilError retry";
            r1.log(r2);
        L_0x0239:
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mDcRetryAlarmController;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mTag;
            r1.startRetryAlarm(r8, r2, r0);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mRetryingState;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x0253:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x0260;
        L_0x0259:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActivatingState: ERR_RilError no retry";
            r0.log(r2);
        L_0x0260:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mInactiveState;
            r2 = r3.mFailCause;
            r0.setEnterNotificationParams(r1, r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x00fe;
        L_0x0278:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "DcActivatingState: stale EVENT_SETUP_DATA_CONNECTION_DONE tag:";
            r2 = r2.append(r3);
            r1 = r1.mTag;
            r1 = r2.append(r1);
            r2 = " != mTag:";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mTag;
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.loge(r1);
            goto L_0x00fe;
        L_0x02a2:
            r0 = r10.obj;
            r0 = (android.os.AsyncResult) r0;
            r1 = r0.userObj;
            r1 = (com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams) r1;
            r2 = r1.mTag;
            r5 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = r5.mTag;
            if (r2 != r5) goto L_0x0425;
        L_0x02b2:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mConnectionParams;
            if (r2 == r1) goto L_0x02e2;
        L_0x02ba:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "DcActivatingState: WEIRD mConnectionsParams:";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.mConnectionParams;
            r5 = r5.append(r6);
            r6 = " != cp:";
            r5 = r5.append(r6);
            r5 = r5.append(r1);
            r5 = r5.toString();
            r2.loge(r5);
        L_0x02e2:
            r2 = com.android.internal.telephony.dataconnection.DcFailCause.UNKNOWN;
            r5 = r0.exception;
            if (r5 != 0) goto L_0x044f;
        L_0x02e8:
            r0 = r0.result;
            r0 = (int[]) r0;
            r0 = (int[]) r0;
            r0 = r0[r3];
            r2 = com.android.internal.telephony.dataconnection.DcFailCause.fromInt(r0);
            r0 = com.android.internal.telephony.TelBrand.IS_KDDI;
            if (r0 == 0) goto L_0x0309;
        L_0x02f8:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r0.mDct;
            r0 = r1.mOnCompletedMsg;
            r0 = r0.obj;
            r0 = (com.android.internal.telephony.dataconnection.ApnContext) r0;
            r0 = (com.android.internal.telephony.dataconnection.ApnContext) r0;
            r3.sendOemKddiFailCauseBroadcast(r2, r0);
        L_0x0309:
            r0 = com.android.internal.telephony.dataconnection.DcFailCause.NONE;
            if (r2 != r0) goto L_0x044f;
        L_0x030d:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x031a;
        L_0x0313:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE BAD: error was NONE, change to UNKNOWN";
            r0.log(r2);
        L_0x031a:
            r0 = com.android.internal.telephony.dataconnection.DcFailCause.UNKNOWN;
        L_0x031c:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2.mDcFailCause = r0;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mRetryManager;
            r2 = r2.getRetryTimer();
            r3 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r3 == 0) goto L_0x036f;
        L_0x032f:
            r3 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE cause=";
            r5 = r5.append(r6);
            r5 = r5.append(r0);
            r6 = " retryDelay=";
            r5 = r5.append(r6);
            r5 = r5.append(r2);
            r6 = " isRetryNeeded=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.mRetryManager;
            r6 = r6.isRetryNeeded();
            r5 = r5.append(r6);
            r6 = " dc=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = r5.append(r6);
            r5 = r5.toString();
            r3.log(r5);
        L_0x036f:
            r3 = r0.isRestartRadioFail();
            if (r3 == 0) goto L_0x03a2;
        L_0x0375:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r2 == 0) goto L_0x0382;
        L_0x037b:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = "DcActivatingState: EVENT_GET_LAST_FAIL_DONE restart radio";
            r2.log(r3);
        L_0x0382:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mDct;
            r2.sendRestartRadio();
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mInactiveState;
            r2.setEnterNotificationParams(r1, r0);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
        L_0x039f:
            r0 = r4;
            goto L_0x0065;
        L_0x03a2:
            r3 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r3.mDct;
            r3 = r3.isPermanentFail(r0);
            if (r3 == 0) goto L_0x03d0;
        L_0x03ae:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r2 == 0) goto L_0x03bb;
        L_0x03b4:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = "DcActivatingState: EVENT_GET_LAST_FAIL_DONE perm er";
            r2.log(r3);
        L_0x03bb:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mInactiveState;
            r2.setEnterNotificationParams(r1, r0);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x039f;
        L_0x03d0:
            if (r2 < 0) goto L_0x0402;
        L_0x03d2:
            r3 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r3.mRetryManager;
            r3 = r3.isRetryNeeded();
            if (r3 == 0) goto L_0x0402;
        L_0x03dc:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r0 == 0) goto L_0x03e9;
        L_0x03e2:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = "DcActivatingState: EVENT_GET_LAST_FAIL_DONE retry";
            r0.log(r1);
        L_0x03e9:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mDcRetryAlarmController;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mTag;
            r0.startRetryAlarm(r8, r1, r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mRetryingState;
            r0.transitionTo(r1);
            goto L_0x039f;
        L_0x0402:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.DBG;
            if (r2 == 0) goto L_0x040f;
        L_0x0408:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = "DcActivatingState: EVENT_GET_LAST_FAIL_DONE no retry";
            r2.log(r3);
        L_0x040f:
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mInactiveState;
            r2.setEnterNotificationParams(r1, r0);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mInactiveState;
            r0.transitionTo(r1);
            goto L_0x039f;
        L_0x0425:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "DcActivatingState: stale EVENT_GET_LAST_FAIL_DONE tag:";
            r2 = r2.append(r3);
            r1 = r1.mTag;
            r1 = r2.append(r1);
            r2 = " != mTag:";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mTag;
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.loge(r1);
            goto L_0x039f;
        L_0x044f:
            r0 = r2;
            goto L_0x031c;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DataConnection$DcActivatingState.processMessage(android.os.Message):boolean");
        }
    }

    private class DcActiveState extends State {
        private DcActiveState() {
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
            DataConnection.this.mNetworkInfo.setDetailedState(DetailedState.CONNECTED, DataConnection.this.mNetworkInfo.getReason(), null);
            DataConnection.this.mNetworkInfo.setExtraInfo(DataConnection.this.mApnSetting.apn);
            DataConnection.this.updateTcpBufferSizes(DataConnection.this.mRilRat);
            NetworkMisc networkMisc = new NetworkMisc();
            networkMisc.subscriberId = DataConnection.this.mPhone.getSubscriberId();
            DataConnection.this.mNetworkAgent = new DcNetworkAgent(DataConnection.this.getHandler().getLooper(), DataConnection.this.mPhone.getContext(), "DcNetworkAgent" + DataConnection.this.mPhone.getSubId(), DataConnection.this.mNetworkInfo, DataConnection.this.makeNetworkCapabilities(), DataConnection.this.mLinkProperties, 50, networkMisc);
        }

        public void exit() {
            if (DataConnection.DBG) {
                DataConnection.this.log("DcActiveState: exit dc=" + this);
            }
            DataConnection.this.mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, DataConnection.this.mNetworkInfo.getReason(), DataConnection.this.mNetworkInfo.getExtraInfo());
            DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
            DataConnection.this.mNetworkAgent = null;
        }

        public boolean processMessage(Message message) {
            DisconnectParams disconnectParams;
            switch (message.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    ConnectionParams connectionParams = (ConnectionParams) message.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState: EVENT_CONNECT cp=" + connectionParams + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(connectionParams.mApnContext)) {
                        DataConnection.this.log("DcActiveState ERROR already added apnContext=" + connectionParams.mApnContext);
                    } else {
                        DataConnection.this.mApnContexts.add(connectionParams.mApnContext);
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        }
                    }
                    DataConnection.this.notifyConnectCompleted(connectionParams, DcFailCause.NONE, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    disconnectParams = (DisconnectParams) message.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState: EVENT_DISCONNECT dp=" + disconnectParams + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(disconnectParams.mApnContext)) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        }
                        if (DataConnection.this.mApnContexts.size() == 1) {
                            DataConnection.this.mApnContexts.clear();
                            DataConnection.this.mDisconnectParams = disconnectParams;
                            DataConnection.this.mConnectionParams = null;
                            disconnectParams.mTag = DataConnection.this.mTag;
                            DataConnection.this.tearDownData(disconnectParams);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                        } else {
                            DataConnection.this.mApnContexts.remove(disconnectParams.mApnContext);
                            DataConnection.this.notifyDisconnectCompleted(disconnectParams, false);
                        }
                    } else {
                        DataConnection.this.log("DcActiveState ERROR no such apnContext=" + disconnectParams.mApnContext + " in this dc=" + DataConnection.this);
                        DataConnection.this.notifyDisconnectCompleted(disconnectParams, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState EVENT_DISCONNECT clearing apn contexts, dc=" + DataConnection.this);
                    }
                    disconnectParams = (DisconnectParams) message.obj;
                    DataConnection.this.mDisconnectParams = disconnectParams;
                    DataConnection.this.mConnectionParams = null;
                    disconnectParams.mTag = DataConnection.this.mTag;
                    DataConnection.this.tearDownData(disconnectParams);
                    DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                    return true;
                case DataConnection.EVENT_LOST_CONNECTION /*262153*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mRetryManager.isRetryNeeded()) {
                        int retryTimer = DataConnection.this.mRetryManager.getRetryTimer();
                        if (TelBrand.IS_KDDI) {
                            retryTimer = 1000;
                        }
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION startRetryAlarm mTag=" + DataConnection.this.mTag + " delay=" + retryTimer + "ms");
                        }
                        DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, retryTimer);
                        DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                    } else {
                        DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /*262156*/:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /*262157*/:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcActiveState not handled msg.what=" + DataConnection.this.getWhatToString(message.what));
                    }
                    return false;
            }
        }
    }

    private class DcDefaultState extends State {
        private DcDefaultState() {
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

        public boolean processMessage(Message message) {
            int i = 0;
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcDefault msg=" + DataConnection.this.getWhatToString(message.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
            }
            int dataNetworkType;
            switch (message.what) {
                case 69633:
                    if (DataConnection.this.mAc == null) {
                        DataConnection.this.mAc = new AsyncChannel();
                        DataConnection.this.mAc.connected(null, DataConnection.this.getHandler(), message.replyTo);
                        if (DataConnection.VDBG) {
                            DataConnection.this.log("DcDefaultState: FULL_CONNECTION reply connected");
                        }
                        DataConnection.this.mAc.replyToMessage(message, 69634, 0, DataConnection.this.mId, "hi");
                        break;
                    }
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("Disconnecting to previous connection mAc=" + DataConnection.this.mAc);
                    }
                    DataConnection.this.mAc.replyToMessage(message, 69634, 3);
                    break;
                case 69636:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("CMD_CHANNEL_DISCONNECTED");
                    }
                    DataConnection.this.quit();
                    break;
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: msg.what=EVENT_CONNECT, fail not expected");
                    }
                    DataConnection.this.notifyConnectCompleted((ConnectionParams) message.obj, DcFailCause.UNKNOWN, false);
                    break;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(message);
                    break;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(message);
                    break;
                case DataConnection.EVENT_TEAR_DOWN_NOW /*262152*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState EVENT_TEAR_DOWN_NOW");
                    }
                    DataConnection.this.mPhone.mCi.deactivateDataCall(DataConnection.this.mCid, 0, null);
                    break;
                case DataConnection.EVENT_LOST_CONNECTION /*262153*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_LOST_CONNECTION tag=" + message.arg1 + ":mTag=" + DataConnection.this.mTag);
                        break;
                    }
                    break;
                case DataConnection.EVENT_RETRY_CONNECTION /*262154*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_RETRY_CONNECTION tag=" + message.arg1 + ":mTag=" + DataConnection.this.mTag);
                        break;
                    }
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /*262155*/:
                    Pair pair = (Pair) ((AsyncResult) message.obj).result;
                    DataConnection.this.mDataRegState = ((Integer) pair.first).intValue();
                    if (DataConnection.this.mRilRat != ((Integer) pair.second).intValue()) {
                        DataConnection.this.updateTcpBufferSizes(((Integer) pair.second).intValue());
                    }
                    DataConnection.this.mRilRat = ((Integer) pair.second).intValue();
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED drs=" + DataConnection.this.mDataRegState + " mRilRat=" + DataConnection.this.mRilRat);
                    }
                    dataNetworkType = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                    DataConnection.this.mNetworkInfo.setSubtype(dataNetworkType, TelephonyManager.getNetworkTypeName(dataNetworkType));
                    if (DataConnection.this.mNetworkAgent != null) {
                        DataConnection.this.mNetworkAgent.sendNetworkCapabilities(DataConnection.this.makeNetworkCapabilities());
                        DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                        DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                        break;
                    }
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /*262156*/:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /*262157*/:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    break;
                case 266240:
                    boolean isInactive = DataConnection.this.getIsInactive();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_IS_INACTIVE  isInactive=" + isInactive);
                    }
                    AsyncChannel access$400 = DataConnection.this.mAc;
                    if (isInactive) {
                        i = 1;
                    }
                    access$400.replyToMessage(message, DcAsyncChannel.RSP_IS_INACTIVE, i);
                    break;
                case DcAsyncChannel.REQ_GET_CID /*266242*/:
                    dataNetworkType = DataConnection.this.getCid();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_CID  cid=" + dataNetworkType);
                    }
                    DataConnection.this.mAc.replyToMessage(message, DcAsyncChannel.RSP_GET_CID, dataNetworkType);
                    break;
                case DcAsyncChannel.REQ_GET_APNSETTING /*266244*/:
                    ApnSetting apnSetting = DataConnection.this.getApnSetting();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_APNSETTING  mApnSetting=" + apnSetting);
                    }
                    DataConnection.this.mAc.replyToMessage(message, DcAsyncChannel.RSP_GET_APNSETTING, apnSetting);
                    break;
                case DcAsyncChannel.REQ_GET_LINK_PROPERTIES /*266246*/:
                    LinkProperties copyLinkProperties = DataConnection.this.getCopyLinkProperties();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_LINK_PROPERTIES linkProperties" + copyLinkProperties);
                    }
                    DataConnection.this.mAc.replyToMessage(message, DcAsyncChannel.RSP_GET_LINK_PROPERTIES, copyLinkProperties);
                    break;
                case DcAsyncChannel.REQ_SET_LINK_PROPERTIES_HTTP_PROXY /*266248*/:
                    ProxyInfo proxyInfo = (ProxyInfo) message.obj;
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_SET_LINK_PROPERTIES_HTTP_PROXY proxy=" + proxyInfo);
                    }
                    DataConnection.this.setLinkPropertiesHttpProxy(proxyInfo);
                    DataConnection.this.mAc.replyToMessage(message, DcAsyncChannel.RSP_SET_LINK_PROPERTIES_HTTP_PROXY);
                    if (DataConnection.this.mNetworkAgent != null) {
                        DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                        break;
                    }
                    break;
                case DcAsyncChannel.REQ_GET_NETWORK_CAPABILITIES /*266250*/:
                    NetworkCapabilities copyNetworkCapabilities = DataConnection.this.getCopyNetworkCapabilities();
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("REQ_GET_NETWORK_CAPABILITIES networkCapabilities" + copyNetworkCapabilities);
                    }
                    DataConnection.this.mAc.replyToMessage(message, DcAsyncChannel.RSP_GET_NETWORK_CAPABILITIES, copyNetworkCapabilities);
                    break;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDefaultState: msg.what=REQ_RESET");
                    }
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    break;
                default:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDefaultState: shouldn't happen but ignore msg.what=" + DataConnection.this.getWhatToString(message.what));
                        break;
                    }
                    break;
            }
            return true;
        }
    }

    private class DcDisconnectingState extends State {
        private DcDisconnectingState() {
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectingState msg.what=EVENT_CONNECT. Defer. RefCount = " + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.deferMessage(message);
                    return true;
                case DataConnection.EVENT_DEACTIVATE_DONE /*262147*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectingState msg.what=EVENT_DEACTIVATE_DONE RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    AsyncResult asyncResult = (AsyncResult) message.obj;
                    DisconnectParams disconnectParams = (DisconnectParams) asyncResult.userObj;
                    if (disconnectParams.mTag == DataConnection.this.mTag) {
                        DataConnection.this.mInactiveState.setEnterNotificationParams((DisconnectParams) asyncResult.userObj);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectState stale EVENT_DEACTIVATE_DONE dp.tag=" + disconnectParams.mTag + " mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDisconnectingState not handled msg.what=" + DataConnection.this.getWhatToString(message.what));
                    }
                    return false;
            }
        }
    }

    private class DcDisconnectionErrorCreatingConnection extends State {
        private DcDisconnectionErrorCreatingConnection() {
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case DataConnection.EVENT_DEACTIVATE_DONE /*262147*/:
                    ConnectionParams connectionParams = (ConnectionParams) ((AsyncResult) message.obj).userObj;
                    if (connectionParams.mTag == DataConnection.this.mTag) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcDisconnectionErrorCreatingConnection msg.what=EVENT_DEACTIVATE_DONE");
                        }
                        DataConnection.this.mInactiveState.setEnterNotificationParams(connectionParams, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection stale EVENT_DEACTIVATE_DONE dp.tag=" + connectionParams.mTag + ", mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection not handled msg.what=" + DataConnection.this.getWhatToString(message.what));
                    }
                    return false;
            }
        }
    }

    private class DcInactiveState extends State {
        private DcInactiveState() {
        }

        public void enter() {
            DataConnection dataConnection = DataConnection.this;
            dataConnection.mTag++;
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

        public boolean processMessage(Message message) {
            switch (message.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: mag.what=EVENT_CONNECT");
                    }
                    ConnectionParams connectionParams = (ConnectionParams) message.obj;
                    if (DataConnection.this.initConnection(connectionParams)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcInactiveState: msg.what=EVENT_CONNECT initConnection failed");
                        }
                        DataConnection.this.notifyConnectCompleted(connectionParams, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT");
                    }
                    if (!TelBrand.IS_KDDI) {
                        DataConnection.this.notifyDisconnectCompleted((DisconnectParams) message.obj, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT_ALL");
                    }
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) message.obj, false);
                    return true;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcInactiveState: msg.what=RSP_RESET, ignore we're already reset");
                    }
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcInactiveState nothandled msg.what=" + DataConnection.this.getWhatToString(message.what));
                    }
                    return false;
            }
        }

        public void setEnterNotificationParams(ConnectionParams connectionParams, DcFailCause dcFailCause) {
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcInactiveState: setEnterNoticationParams cp,cause");
            }
            DataConnection.this.mConnectionParams = connectionParams;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = dcFailCause;
        }

        public void setEnterNotificationParams(DisconnectParams disconnectParams) {
            if (DataConnection.VDBG) {
                DataConnection.this.log("DcInactiveState: setEnterNoticationParams dp");
            }
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = disconnectParams;
            DataConnection.this.mDcFailCause = DcFailCause.NONE;
        }

        public void setEnterNotificationParams(DcFailCause dcFailCause) {
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = dcFailCause;
        }
    }

    private class DcNetworkAgent extends NetworkAgent {
        public DcNetworkAgent(Looper looper, Context context, String str, NetworkInfo networkInfo, NetworkCapabilities networkCapabilities, LinkProperties linkProperties, int i, NetworkMisc networkMisc) {
            super(looper, context, str, networkInfo, networkCapabilities, linkProperties, i, networkMisc);
        }

        /* Access modifiers changed, original: protected */
        public void unwanted() {
            if (DataConnection.this.mNetworkAgent != this) {
                log("unwanted found mNetworkAgent=" + DataConnection.this.mNetworkAgent + ", which isn't me.  Aborting unwanted");
            } else if (DataConnection.this.mApnContexts != null) {
                for (ApnContext apnContext : DataConnection.this.mApnContexts) {
                    DataConnection.this.sendMessage(DataConnection.this.obtainMessage(DataConnection.EVENT_DISCONNECT, new DisconnectParams(apnContext, apnContext.getReason(), DataConnection.this.mDct.obtainMessage(270351, apnContext))));
                }
            }
        }
    }

    private class DcRetryingState extends State {
        private DcRetryingState() {
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

        public boolean processMessage(Message message) {
            switch (message.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    ConnectionParams connectionParams = (ConnectionParams) message.obj;
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size() + " cp=" + connectionParams + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                    }
                    if (DataConnection.this.initConnection(connectionParams)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT initConnection failed");
                        }
                        DataConnection.this.notifyConnectCompleted(connectionParams, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    DisconnectParams disconnectParams = (DisconnectParams) message.obj;
                    if (DataConnection.this.mApnContexts.remove(disconnectParams.mApnContext) && DataConnection.this.mApnContexts.size() == 0) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT  RefCount=" + DataConnection.this.mApnContexts.size() + " dp=" + disconnectParams);
                        }
                        DataConnection.this.mInactiveState.setEnterNotificationParams(disconnectParams);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: msg.what=EVENT_DISCONNECT");
                        }
                        DataConnection.this.notifyDisconnectCompleted(disconnectParams, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT/DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                case DataConnection.EVENT_RETRY_CONNECTION /*262154*/:
                    if (message.arg1 == DataConnection.this.mTag) {
                        DataConnection.this.mRetryManager.increaseRetryCount();
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState EVENT_RETRY_CONNECTION RetryCount=" + DataConnection.this.mRetryManager.getRetryCount() + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                        }
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState stale EVENT_RETRY_CONNECTION tag:" + message.arg1 + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /*262155*/:
                    Pair pair = (Pair) ((AsyncResult) message.obj).result;
                    int intValue = ((Integer) pair.first).intValue();
                    int intValue2 = ((Integer) pair.second).intValue();
                    if (intValue2 == DataConnection.this.mRilRat && intValue == DataConnection.this.mDataRegState) {
                        if (DataConnection.DBG) {
                            DataConnection.this.log("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED strange no change in drs=" + intValue + " rat=" + intValue2 + " ignoring");
                        }
                    } else if (DataConnection.this.mConnectionParams.mRetryWhenSSChange) {
                        return false;
                    } else {
                        if (intValue != 0) {
                            DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                            DataConnection.this.deferMessage(message);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            if (DataConnection.DBG) {
                                DataConnection.this.logAndAddLogRec("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED giving up changed from " + DataConnection.this.mRilRat + " to rat=" + intValue2 + " or drs changed from " + DataConnection.this.mDataRegState + " to drs=" + intValue);
                            }
                        }
                        DataConnection.this.mDataRegState = intValue;
                        DataConnection.this.mRilRat = intValue2;
                        intValue2 = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                        DataConnection.this.mNetworkInfo.setSubtype(intValue2, TelephonyManager.getNetworkTypeName(intValue2));
                    }
                    return true;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    if (DataConnection.DBG) {
                        DataConnection.this.log("DcRetryingState: msg.what=RSP_RESET, ignore we're already reset");
                    }
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DataConnection.this.mConnectionParams, DcFailCause.RESET_BY_FRAMEWORK);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                default:
                    if (DataConnection.VDBG) {
                        DataConnection.this.log("DcRetryingState nothandled msg.what=" + DataConnection.this.getWhatToString(message.what));
                    }
                    return false;
            }
        }
    }

    static class DisconnectParams {
        ApnContext mApnContext;
        Message mOnCompletedMsg;
        String mReason;
        int mTag;

        DisconnectParams(ApnContext apnContext, String str, Message message) {
            this.mApnContext = apnContext;
            this.mReason = str;
            this.mOnCompletedMsg = message;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mReason=" + this.mReason + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    static class UpdateLinkPropertyResult {
        public LinkProperties newLp;
        public LinkProperties oldLp;
        public SetupResult setupResult = SetupResult.SUCCESS;

        public UpdateLinkPropertyResult(LinkProperties linkProperties) {
            this.oldLp = linkProperties;
            this.newLp = linkProperties;
        }
    }

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

    private DataConnection(PhoneBase phoneBase, String str, int i, DcTrackerBase dcTrackerBase, DcTesterFailBringUpAll dcTesterFailBringUpAll, DcController dcController) {
        super(str, dcController.getHandler());
        if (!"eng".equals(Build.TYPE)) {
            DBG = false;
            VDBG = false;
        }
        setLogRecSize(300);
        setLogOnlyTransitions(true);
        if (DBG) {
            log("DataConnection constructor E");
        }
        this.mPhone = phoneBase;
        this.mDct = dcTrackerBase;
        this.mDcTesterFailBringUpAll = dcTesterFailBringUpAll;
        this.mDcController = dcController;
        this.mId = i;
        this.mCid = -1;
        this.mDcRetryAlarmController = new DcRetryAlarmController(this.mPhone, this);
        ServiceState serviceState = this.mPhone.getServiceState();
        this.mRilRat = serviceState.getRilDataRadioTechnology();
        this.mDataRegState = this.mPhone.getServiceState().getDataRegState();
        int dataNetworkType = serviceState.getDataNetworkType();
        this.mNetworkInfo = new NetworkInfo(0, dataNetworkType, NETWORK_TYPE, TelephonyManager.getNetworkTypeName(dataNetworkType));
        this.mNetworkInfo.setRoaming(serviceState.getDataRoaming());
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

    private void checkSetMtu(ApnSetting apnSetting, LinkProperties linkProperties) {
        if (linkProperties != null && apnSetting != null && linkProperties != null) {
            if (linkProperties.getMtu() != 0) {
                if (DBG) {
                    log("MTU set by call response to: " + linkProperties.getMtu());
                }
            } else if (apnSetting == null || apnSetting.mtu == 0) {
                int integer = this.mPhone.getContext().getResources().getInteger(17694857);
                if (integer != 0) {
                    linkProperties.setMtu(integer);
                    if (DBG) {
                        log("MTU set by config resource to: " + integer);
                    }
                }
            } else {
                linkProperties.setMtu(apnSetting.mtu);
                if (DBG) {
                    log("MTU set by APN to: " + apnSetting.mtu);
                }
            }
        }
    }

    private void clearSettings() {
        if (DBG) {
            log("clearSettings");
        }
        this.mCreateTime = -1;
        this.mLastFailTime = -1;
        this.mLastFailCause = DcFailCause.NONE;
        this.mCid = -1;
        this.mPcscfAddr = new String[5];
        this.mLinkProperties = new LinkProperties();
        this.mApnContexts.clear();
        this.mApnSetting = null;
        this.mDcFailCause = null;
    }

    static String cmdToString(int i) {
        int i2 = i - SmsEnvelope.TELESERVICE_MWI;
        String cmdToString = (i2 < 0 || i2 >= sCmdToString.length) ? DcAsyncChannel.cmdToString(i2 + SmsEnvelope.TELESERVICE_MWI) : sCmdToString[i2];
        return cmdToString == null ? "0x" + Integer.toHexString(i2 + SmsEnvelope.TELESERVICE_MWI) : cmdToString;
    }

    private void configureRetry(boolean z) {
        if (!this.mRetryManager.configure(getRetryConfig(z))) {
            if (z) {
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
            log("configureRetry: forDefault=" + z + " mRetryManager=" + this.mRetryManager);
        }
    }

    private String getRetryConfig(boolean z) {
        int networkType = this.mPhone.getServiceState().getNetworkType();
        if (Build.IS_DEBUGGABLE) {
            String str = SystemProperties.get("test.data_retry_config");
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return (networkType == 4 || networkType == 7 || networkType == 5 || networkType == 6 || networkType == 12 || networkType == 14) ? SystemProperties.get("ro.cdma.data_retry_config") : z ? SystemProperties.get("ro.gsm.data_retry_config") : SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    private boolean initConnection(ConnectionParams connectionParams) {
        ApnContext apnContext = connectionParams.mApnContext;
        if (this.mApnSetting == null) {
            this.mApnSetting = apnContext.getApnSetting();
        }
        if (this.mApnSetting == null || !this.mApnSetting.canHandleType(apnContext.getApnType())) {
            if (DBG) {
                log("initConnection: incompatible apnSetting in ConnectionParams cp=" + connectionParams + " dc=" + this);
            }
            return false;
        }
        this.mTag++;
        this.mConnectionParams = connectionParams;
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

    private boolean isDnsOk(String[] strArr) {
        if (!NULL_IP.equals(strArr[0]) || !NULL_IP.equals(strArr[1]) || this.mPhone.isDnsCheckDisabled() || (this.mApnSetting.types[0].equals("mms") && isIpAddress(this.mApnSetting.mmsProxy))) {
            return true;
        }
        log(String.format("isDnsOk: return false apn.types[0]=%s APN_TYPE_MMS=%s isIpAddress(%s)=%s", new Object[]{this.mApnSetting.types[0], "mms", this.mApnSetting.mmsProxy, Boolean.valueOf(isIpAddress(this.mApnSetting.mmsProxy))}));
        return false;
    }

    private boolean isIpAddress(String str) {
        return str == null ? false : Patterns.IP_ADDRESS.matcher(str).matches();
    }

    static DataConnection makeDataConnection(PhoneBase phoneBase, int i, DcTrackerBase dcTrackerBase, DcTesterFailBringUpAll dcTesterFailBringUpAll, DcController dcController) {
        DataConnection dataConnection = new DataConnection(phoneBase, "DC-" + mInstanceNumber.incrementAndGet(), i, dcTrackerBase, dcTesterFailBringUpAll, dcController);
        dataConnection.start();
        if (DBG) {
            dataConnection.log("Made " + dataConnection.getName());
        }
        return dataConnection;
    }

    private NetworkCapabilities makeNetworkCapabilities() {
        int i;
        int i2;
        NetworkCapabilities networkCapabilities = new NetworkCapabilities();
        networkCapabilities.addTransportType(0);
        if (this.mApnSetting != null) {
            for (String str : this.mApnSetting.types) {
                i = -1;
                switch (str.hashCode()) {
                    case 42:
                        if (str.equals(CharacterSets.MIMENAME_ANY_CHARSET)) {
                            i = 0;
                            break;
                        }
                        break;
                    case 3352:
                        if (str.equals("ia")) {
                            i = 8;
                            break;
                        }
                        break;
                    case 98292:
                        if (str.equals("cbs")) {
                            i = 7;
                            break;
                        }
                        break;
                    case 99837:
                        if (str.equals("dun")) {
                            i = 4;
                            break;
                        }
                        break;
                    case 104399:
                        if (str.equals("ims")) {
                            i = 6;
                            break;
                        }
                        break;
                    case 108243:
                        if (str.equals("mms")) {
                            i = 2;
                            break;
                        }
                        break;
                    case 3149046:
                        if (str.equals("fota")) {
                            i = 5;
                            break;
                        }
                        break;
                    case 3541982:
                        if (str.equals("supl")) {
                            i = 3;
                            break;
                        }
                        break;
                    case 1544803905:
                        if (str.equals("default")) {
                            i = 1;
                            break;
                        }
                        break;
                }
                switch (i) {
                    case 0:
                        networkCapabilities.addCapability(12);
                        networkCapabilities.addCapability(0);
                        networkCapabilities.addCapability(1);
                        networkCapabilities.addCapability(3);
                        networkCapabilities.addCapability(4);
                        networkCapabilities.addCapability(5);
                        networkCapabilities.addCapability(7);
                        if (TelBrand.IS_KDDI) {
                            networkCapabilities.addCapability(2);
                            break;
                        }
                        break;
                    case 1:
                        networkCapabilities.addCapability(12);
                        break;
                    case 2:
                        networkCapabilities.addCapability(0);
                        break;
                    case 3:
                        networkCapabilities.addCapability(1);
                        break;
                    case 4:
                        ApnSetting fetchDunApn = this.mDct.fetchDunApn();
                        if (fetchDunApn == null || fetchDunApn.equals(this.mApnSetting)) {
                            networkCapabilities.addCapability(2);
                            break;
                        }
                    case 5:
                        networkCapabilities.addCapability(3);
                        break;
                    case 6:
                        networkCapabilities.addCapability(4);
                        break;
                    case 7:
                        networkCapabilities.addCapability(5);
                        break;
                    case 8:
                        networkCapabilities.addCapability(7);
                        break;
                }
                if (this.mPhone.getSubId() != SubscriptionManager.getDefaultDataSubId()) {
                    log("DataConnection on non-dds does not have INTERNET capability.");
                    networkCapabilities.removeCapability(12);
                }
            }
            ConnectivityManager.maybeMarkCapabilitiesRestricted(networkCapabilities);
        }
        switch (this.mRilRat) {
            case 1:
                i = 80;
                i2 = 80;
                break;
            case 2:
                i = 59;
                i2 = 236;
                break;
            case 3:
                i = 384;
                i2 = 384;
                break;
            case 4:
            case 5:
                i = 14;
                i2 = 14;
                break;
            case 6:
                i = 100;
                i2 = 100;
                break;
            case 7:
                i = 153;
                i2 = 2457;
                break;
            case 8:
                i = 1843;
                i2 = 3174;
                break;
            case 9:
                i = 2048;
                i2 = 14336;
                break;
            case 10:
                i = 5898;
                i2 = 14336;
                break;
            case 11:
                i = 5898;
                i2 = 14336;
                break;
            case 12:
                i = 1843;
                i2 = 5017;
                break;
            case 13:
                i = 153;
                i2 = 2516;
                break;
            case 14:
                i = 51200;
                i2 = 102400;
                break;
            case 15:
                i = 11264;
                i2 = 43008;
                break;
            case 16:
            case 17:
            case 18:
                i = 14;
                i2 = 14;
                break;
            case 19:
                i = 51200;
                i2 = 102400;
                break;
            default:
                i = 14;
                i2 = 14;
                break;
        }
        networkCapabilities.setLinkUpstreamBandwidthKbps(i);
        networkCapabilities.setLinkDownstreamBandwidthKbps(i2);
        networkCapabilities.setNetworkSpecifier("" + this.mPhone.getSubId());
        return networkCapabilities;
    }

    private static String msgToString(Message message) {
        if (message == null) {
            return "null";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{what=");
        stringBuilder.append(cmdToString(message.what));
        stringBuilder.append(" when=");
        TimeUtils.formatDuration(message.getWhen() - SystemClock.uptimeMillis(), stringBuilder);
        if (message.arg1 != 0) {
            stringBuilder.append(" arg1=");
            stringBuilder.append(message.arg1);
        }
        if (message.arg2 != 0) {
            stringBuilder.append(" arg2=");
            stringBuilder.append(message.arg2);
        }
        if (message.obj != null) {
            stringBuilder.append(" obj=");
            stringBuilder.append(message.obj);
        }
        stringBuilder.append(" target=");
        stringBuilder.append(message.getTarget());
        stringBuilder.append(" replyTo=");
        stringBuilder.append(message.replyTo);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    private void notifyAllDisconnectCompleted(DcFailCause dcFailCause) {
        notifyAllWithEvent(null, 270351, dcFailCause.toString());
    }

    private void notifyAllOfConnected(String str) {
        notifyAllWithEvent(null, 270336, str);
    }

    private void notifyAllOfDisconnectDcRetrying(String str) {
        notifyAllWithEvent(null, 270370, str);
    }

    private void notifyAllWithEvent(ApnContext apnContext, int i, String str) {
        this.mNetworkInfo.setDetailedState(this.mNetworkInfo.getDetailedState(), str, this.mNetworkInfo.getExtraInfo());
        for (ApnContext apnContext2 : this.mApnContexts) {
            if (apnContext2 != apnContext) {
                if (str != null) {
                    apnContext2.setReason(str);
                }
                Message obtainMessage = this.mDct.obtainMessage(i, apnContext2);
                AsyncResult.forMessage(obtainMessage);
                obtainMessage.sendToTarget();
            }
        }
    }

    private void notifyConnectCompleted(ConnectionParams connectionParams, DcFailCause dcFailCause, boolean z) {
        ApnContext apnContext = null;
        if (!(connectionParams == null || connectionParams.mOnCompletedMsg == null)) {
            Message message = connectionParams.mOnCompletedMsg;
            connectionParams.mOnCompletedMsg = null;
            if (message.obj instanceof ApnContext) {
                apnContext = (ApnContext) message.obj;
            }
            long currentTimeMillis = System.currentTimeMillis();
            message.arg1 = this.mCid;
            if (dcFailCause == DcFailCause.NONE) {
                this.mCreateTime = currentTimeMillis;
                AsyncResult.forMessage(message);
            } else {
                this.mLastFailCause = dcFailCause;
                this.mLastFailTime = currentTimeMillis;
                if (dcFailCause == null) {
                    dcFailCause = DcFailCause.UNKNOWN;
                }
                AsyncResult.forMessage(message, dcFailCause, new Throwable(dcFailCause.toString()));
            }
            if (DBG) {
                log("notifyConnectCompleted at " + currentTimeMillis + " cause=" + dcFailCause + " connectionCompletedMsg=" + msgToString(message));
            }
            message.sendToTarget();
        }
        if (z) {
            notifyAllWithEvent(apnContext, 270371, dcFailCause.toString());
        }
    }

    private void notifyDisconnectCompleted(DisconnectParams disconnectParams, boolean z) {
        String str;
        ApnContext apnContext;
        if (VDBG) {
            log("NotifyDisconnectCompleted");
        }
        if (disconnectParams == null || disconnectParams.mOnCompletedMsg == null) {
            str = null;
            apnContext = null;
        } else {
            Message message = disconnectParams.mOnCompletedMsg;
            disconnectParams.mOnCompletedMsg = null;
            apnContext = message.obj instanceof ApnContext ? (ApnContext) message.obj : null;
            str = disconnectParams.mReason;
            if (VDBG) {
                String message2 = message.toString();
                String str2 = message.obj instanceof String ? (String) message.obj : "<no-reason>";
                log(String.format("msg=%s msg.obj=%s", new Object[]{message2, str2}));
            }
            AsyncResult.forMessage(message);
            message.sendToTarget();
        }
        if (z) {
            notifyAllWithEvent(apnContext, 270351, str == null ? DcFailCause.UNKNOWN.toString() : str);
        }
        if (DBG) {
            log("NotifyDisconnectCompleted DisconnectParams=" + disconnectParams);
        }
    }

    private void onConnect(ConnectionParams connectionParams) {
        if (DBG) {
            log("onConnect: carrier='" + this.mApnSetting.carrier + "' APN='" + this.mApnSetting.apn + "' proxy='" + this.mApnSetting.proxy + "' port='" + this.mApnSetting.port + "'");
        }
        if (this.mDcTesterFailBringUpAll.getDcFailBringUp().mCounter > 0) {
            DataCallResponse dataCallResponse = new DataCallResponse();
            dataCallResponse.version = this.mPhone.mCi.getRilVersion();
            dataCallResponse.status = this.mDcTesterFailBringUpAll.getDcFailBringUp().mFailCause.getErrorCode();
            dataCallResponse.cid = 0;
            dataCallResponse.active = 0;
            dataCallResponse.type = "";
            dataCallResponse.ifname = "";
            dataCallResponse.addresses = new String[0];
            dataCallResponse.dnses = new String[0];
            dataCallResponse.gateways = new String[0];
            dataCallResponse.suggestedRetryTime = this.mDcTesterFailBringUpAll.getDcFailBringUp().mSuggestedRetryTime;
            dataCallResponse.pcscf = new String[0];
            dataCallResponse.mtu = 0;
            Message obtainMessage = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, connectionParams);
            AsyncResult.forMessage(obtainMessage, dataCallResponse, null);
            sendMessage(obtainMessage);
            if (DBG) {
                log("onConnect: FailBringUpAll=" + this.mDcTesterFailBringUpAll.getDcFailBringUp() + " send error response=" + dataCallResponse);
            }
            DcFailBringUp dcFailBringUp = this.mDcTesterFailBringUpAll.getDcFailBringUp();
            dcFailBringUp.mCounter--;
            return;
        }
        int profileId;
        int i;
        this.mCreateTime = -1;
        this.mLastFailTime = -1;
        this.mLastFailCause = DcFailCause.NONE;
        if (this.mApnSetting.getApnProfileType() == ApnProfileType.PROFILE_TYPE_OMH) {
            profileId = this.mApnSetting.getProfileId() + 1000;
            log("OMH profile, dataProfile id = " + profileId);
            i = profileId;
        } else {
            i = connectionParams.mProfileId;
        }
        Message obtainMessage2 = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, connectionParams);
        obtainMessage2.obj = connectionParams;
        profileId = this.mApnSetting.authType;
        int i2 = profileId == -1 ? TextUtils.isEmpty(this.mApnSetting.user) ? 0 : 3 : profileId;
        this.mPhone.mCi.setupDataCall(Integer.toString(connectionParams.mRilRat + 2), Integer.toString(i), this.mApnSetting.apn, this.mApnSetting.user, this.mApnSetting.password, Integer.toString(i2), this.mPhone.getServiceState().getDataRoaming() ? this.mApnSetting.roamingProtocol : this.mApnSetting.protocol, obtainMessage2);
    }

    private SetupResult onSetupConnectionCompleted(AsyncResult asyncResult) {
        DataCallResponse dataCallResponse = (DataCallResponse) asyncResult.result;
        ConnectionParams connectionParams = (ConnectionParams) asyncResult.userObj;
        SetupResult setupResult;
        if (connectionParams.mTag != this.mTag) {
            if (DBG) {
                log("onSetupConnectionCompleted stale cp.tag=" + connectionParams.mTag + ", mtag=" + this.mTag);
            }
            return SetupResult.ERR_Stale;
        } else if (asyncResult.exception != null) {
            if (DBG) {
                log("onSetupConnectionCompleted failed, ar.exception=" + asyncResult.exception + " response=" + dataCallResponse);
            }
            if ((asyncResult.exception instanceof CommandException) && ((CommandException) asyncResult.exception).getCommandError() == Error.RADIO_NOT_AVAILABLE) {
                SetupResult setupResult2 = SetupResult.ERR_BadCommand;
                setupResult2.mFailCause = DcFailCause.RADIO_NOT_AVAILABLE;
                return setupResult2;
            } else if (dataCallResponse == null || dataCallResponse.version < 4) {
                return SetupResult.ERR_GetLastErrorFromRil;
            } else {
                setupResult = SetupResult.ERR_RilError;
                setupResult.mFailCause = DcFailCause.fromInt(dataCallResponse.status);
                return setupResult;
            }
        } else if (dataCallResponse.status != 0) {
            setupResult = SetupResult.ERR_RilError;
            setupResult.mFailCause = DcFailCause.fromInt(dataCallResponse.status);
            return setupResult;
        } else {
            if (DBG) {
                log("onSetupConnectionCompleted received DataCallResponse: " + dataCallResponse);
            }
            this.mCid = dataCallResponse.cid;
            this.mPcscfAddr = dataCallResponse.pcscf;
            return updateLinkProperty(dataCallResponse).setupResult;
        }
    }

    private SetupResult setLinkProperties(DataCallResponse dataCallResponse, LinkProperties linkProperties) {
        String str = "net." + dataCallResponse.ifname + ".";
        return dataCallResponse.setLinkProperties(linkProperties, isDnsOk(new String[]{SystemProperties.get(str + "dns1"), SystemProperties.get(str + "dns2")}));
    }

    static void slog(String str) {
        Rlog.d("DC", str);
    }

    /* JADX WARNING: Removed duplicated region for block: B:13:0x0039  */
    private void tearDownData(java.lang.Object r7) {
        /*
        r6 = this;
        r4 = 0;
        r5 = 262147; // 0x40003 float:3.67346E-40 double:1.29518E-318;
        r1 = 0;
        if (r7 == 0) goto L_0x0072;
    L_0x0007:
        r0 = r7 instanceof com.android.internal.telephony.dataconnection.DataConnection.DisconnectParams;
        if (r0 == 0) goto L_0x0072;
    L_0x000b:
        r0 = r7;
        r0 = (com.android.internal.telephony.dataconnection.DataConnection.DisconnectParams) r0;
        r2 = r0.mReason;
        r3 = "radioTurnedOff";
        r2 = android.text.TextUtils.equals(r2, r3);
        if (r2 == 0) goto L_0x004e;
    L_0x0018:
        r0 = 1;
    L_0x0019:
        r2 = r6.mPhone;
        r2 = r2.mCi;
        r2 = r2.getRadioState();
        r2 = r2.isOn();
        if (r2 != 0) goto L_0x0035;
    L_0x0027:
        r2 = r6.mPhone;
        r2 = r2.getServiceState();
        r2 = r2.getRilDataRadioTechnology();
        r3 = 18;
        if (r2 != r3) goto L_0x005a;
    L_0x0035:
        r2 = DBG;
        if (r2 == 0) goto L_0x003e;
    L_0x0039:
        r2 = "tearDownData radio is on, call deactivateDataCall";
        r6.log(r2);
    L_0x003e:
        r2 = r6.mPhone;
        r2 = r2.mCi;
        r3 = r6.mCid;
        r4 = r6.mTag;
        r1 = r6.obtainMessage(r5, r4, r1, r7);
        r2.deactivateDataCall(r3, r0, r1);
    L_0x004d:
        return;
    L_0x004e:
        r0 = r0.mReason;
        r2 = "pdpReset";
        r0 = android.text.TextUtils.equals(r0, r2);
        if (r0 == 0) goto L_0x0072;
    L_0x0058:
        r0 = 2;
        goto L_0x0019;
    L_0x005a:
        r0 = DBG;
        if (r0 == 0) goto L_0x0063;
    L_0x005e:
        r0 = "tearDownData radio is off sendMessage EVENT_DEACTIVATE_DONE immediately";
        r6.log(r0);
    L_0x0063:
        r0 = new android.os.AsyncResult;
        r0.<init>(r7, r4, r4);
        r2 = r6.mTag;
        r0 = r6.obtainMessage(r5, r2, r1, r0);
        r6.sendMessage(r0);
        goto L_0x004d;
    L_0x0072:
        r0 = r1;
        goto L_0x0019;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DataConnection.tearDownData(java.lang.Object):void");
    }

    private void updateTcpBufferSizes(int i) {
        String toLowerCase = ServiceState.rilRadioTechnologyToString(i).toLowerCase(Locale.ROOT);
        if (i == 7 || i == 8 || i == 12) {
            toLowerCase = "evdo";
        }
        String[] stringArray = this.mPhone.getContext().getResources().getStringArray(17236014);
        for (String split : stringArray) {
            String[] split2 = split.split(":");
            if (toLowerCase.equals(split2[0]) && split2.length == 2) {
                toLowerCase = split2[1];
                break;
            }
        }
        toLowerCase = null;
        if (toLowerCase == null) {
            switch (i) {
                case 1:
                    toLowerCase = TCP_BUFFER_SIZES_GPRS;
                    break;
                case 2:
                    toLowerCase = TCP_BUFFER_SIZES_EDGE;
                    break;
                case 3:
                    toLowerCase = TCP_BUFFER_SIZES_UMTS;
                    break;
                case 6:
                    toLowerCase = TCP_BUFFER_SIZES_1XRTT;
                    break;
                case 7:
                case 8:
                case 12:
                    toLowerCase = TCP_BUFFER_SIZES_EVDO;
                    break;
                case 9:
                    toLowerCase = TCP_BUFFER_SIZES_HSDPA;
                    break;
                case 10:
                case 11:
                    toLowerCase = TCP_BUFFER_SIZES_HSPA;
                    break;
                case 13:
                    toLowerCase = TCP_BUFFER_SIZES_EHRPD;
                    break;
                case 14:
                case 19:
                    toLowerCase = TCP_BUFFER_SIZES_LTE;
                    break;
                case 15:
                    toLowerCase = TCP_BUFFER_SIZES_HSPAP;
                    break;
            }
        }
        this.mLinkProperties.setTcpBufferSizes(toLowerCase);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.print("DataConnection ");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mApnContexts.size=" + this.mApnContexts.size());
        printWriter.println(" mApnContexts=" + this.mApnContexts);
        printWriter.flush();
        printWriter.println(" mDataConnectionTracker=" + this.mDct);
        printWriter.println(" mApnSetting=" + this.mApnSetting);
        printWriter.println(" mTag=" + this.mTag);
        printWriter.println(" mCid=" + this.mCid);
        printWriter.println(" mRetryManager=" + this.mRetryManager);
        printWriter.println(" mConnectionParams=" + this.mConnectionParams);
        printWriter.println(" mDisconnectParams=" + this.mDisconnectParams);
        printWriter.println(" mDcFailCause=" + this.mDcFailCause);
        printWriter.flush();
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.flush();
        printWriter.println(" mLinkProperties=" + this.mLinkProperties);
        printWriter.flush();
        printWriter.println(" mDataRegState=" + this.mDataRegState);
        printWriter.println(" mRilRat=" + this.mRilRat);
        printWriter.println(" mNetworkCapabilities=" + makeNetworkCapabilities());
        printWriter.println(" mCreateTime=" + TimeUtils.logTimeOfDay(this.mCreateTime));
        printWriter.println(" mLastFailTime=" + TimeUtils.logTimeOfDay(this.mLastFailTime));
        printWriter.println(" mLastFailCause=" + this.mLastFailCause);
        printWriter.flush();
        printWriter.println(" mUserData=" + this.mUserData);
        printWriter.println(" mInstanceNumber=" + mInstanceNumber);
        printWriter.println(" mAc=" + this.mAc);
        printWriter.println(" mDcRetryAlarmController=" + this.mDcRetryAlarmController);
        printWriter.flush();
    }

    /* Access modifiers changed, original: 0000 */
    public ApnSetting getApnSetting() {
        return this.mApnSetting;
    }

    /* Access modifiers changed, original: 0000 */
    public int getCid() {
        return this.mCid;
    }

    /* Access modifiers changed, original: 0000 */
    public LinkProperties getCopyLinkProperties() {
        return new LinkProperties(this.mLinkProperties);
    }

    /* Access modifiers changed, original: 0000 */
    public NetworkCapabilities getCopyNetworkCapabilities() {
        return makeNetworkCapabilities();
    }

    public int getDataConnectionId() {
        return this.mId;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean getIsInactive() {
        return getCurrentState() == this.mInactiveState;
    }

    /* Access modifiers changed, original: protected */
    public String getWhatToString(int i) {
        return cmdToString(i);
    }

    public boolean isIpv4Connected() {
        for (InetAddress inetAddress : this.mLinkProperties.getAddresses()) {
            if (inetAddress instanceof Inet4Address) {
                Inet4Address inet4Address = (Inet4Address) inetAddress;
                if (!(inet4Address.isAnyLocalAddress() || inet4Address.isLinkLocalAddress() || inet4Address.isLoopbackAddress() || inet4Address.isMulticastAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isIpv6Connected() {
        for (InetAddress inetAddress : this.mLinkProperties.getAddresses()) {
            if (inetAddress instanceof Inet6Address) {
                Inet6Address inet6Address = (Inet6Address) inetAddress;
                if (!(inet6Address.isAnyLocalAddress() || inet6Address.isLinkLocalAddress() || inet6Address.isLoopbackAddress() || inet6Address.isMulticastAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void logd(String str) {
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
    public void logi(String str) {
        Rlog.i(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void logv(String str) {
        Rlog.v(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void logw(String str) {
        Rlog.w(getName(), str);
    }

    /* Access modifiers changed, original: 0000 */
    public void setLinkPropertiesHttpProxy(ProxyInfo proxyInfo) {
        this.mLinkProperties.setHttpProxy(proxyInfo);
    }

    /* Access modifiers changed, original: 0000 */
    public void tearDownNow() {
        if (DBG) {
            log("tearDownNow()");
        }
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_NOW));
    }

    public String toString() {
        return "{" + toStringSimple() + " mApnContexts=" + this.mApnContexts + "}";
    }

    public String toStringSimple() {
        return getName() + ": State=" + getCurrentState().getName() + " mApnSetting=" + this.mApnSetting + " RefCount=" + this.mApnContexts.size() + " mCid=" + this.mCid + " mCreateTime=" + this.mCreateTime + " mLastastFailTime=" + this.mLastFailTime + " mLastFailCause=" + this.mLastFailCause + " mTag=" + this.mTag + " mRetryManager=" + this.mRetryManager + " mLinkProperties=" + this.mLinkProperties + " linkCapabilities=" + makeNetworkCapabilities();
    }

    /* Access modifiers changed, original: 0000 */
    public UpdateLinkPropertyResult updateLinkProperty(DataCallResponse dataCallResponse) {
        UpdateLinkPropertyResult updateLinkPropertyResult = new UpdateLinkPropertyResult(this.mLinkProperties);
        if (dataCallResponse != null) {
            updateLinkPropertyResult.newLp = new LinkProperties();
            if (TelBrand.IS_KDDI) {
                int i;
                if (this.mApnSetting.oemDnses != null) {
                    String trim;
                    ArrayList arrayList = new ArrayList();
                    for (String trim2 : this.mApnSetting.oemDnses) {
                        if (trim2 != null) {
                            trim2 = trim2.trim();
                            if (!trim2.isEmpty()) {
                                arrayList.add(trim2);
                            }
                        }
                    }
                    if (dataCallResponse.dnses != null) {
                        for (String trim22 : dataCallResponse.dnses) {
                            if (trim22 != null && !trim22.trim().isEmpty()) {
                                i = 1;
                                break;
                            }
                        }
                    }
                    i = 0;
                    if (i == 0 && arrayList.size() == 0) {
                        arrayList.add("");
                    }
                    if (arrayList.size() != 0) {
                        dataCallResponse.dnses = (String[]) arrayList.toArray(new String[0]);
                    }
                }
                if (this.mPhone.mDcTracker.mEnableApnCarNavi) {
                    this.mPhone.mDcTracker.mLocalAddressCarNavi = "";
                    this.mPhone.mDcTracker.mDnsAddressCarNavi[0] = "";
                    this.mPhone.mDcTracker.mDnsAddressCarNavi[1] = "";
                    if (dataCallResponse.addresses != null && dataCallResponse.addresses.length > 0) {
                        for (String trim3 : dataCallResponse.addresses) {
                            String trim32 = trim32.trim();
                            if (!trim32.isEmpty()) {
                                String[] split = trim32.split("/");
                                if (split.length == 2) {
                                    this.mPhone.mDcTracker.mLocalAddressCarNavi = split[0];
                                }
                            }
                        }
                    }
                    if (dataCallResponse.dnses != null && dataCallResponse.dnses.length > 0) {
                        this.mPhone.mDcTracker.mDnsAddressCarNavi[0] = dataCallResponse.dnses[0].trim();
                    }
                    if (dataCallResponse.dnses != null && dataCallResponse.dnses.length > 1) {
                        this.mPhone.mDcTracker.mDnsAddressCarNavi[1] = dataCallResponse.dnses[1].trim();
                    }
                }
            }
            updateLinkPropertyResult.setupResult = setLinkProperties(dataCallResponse, updateLinkPropertyResult.newLp);
            if (updateLinkPropertyResult.setupResult == SetupResult.SUCCESS) {
                updateLinkPropertyResult.newLp.setHttpProxy(this.mLinkProperties.getHttpProxy());
                checkSetMtu(this.mApnSetting, updateLinkPropertyResult.newLp);
                if (TelBrand.IS_KDDI && this.mApnSetting.oemDnses != null && (updateLinkPropertyResult.newLp.getDnsServers() == null || updateLinkPropertyResult.newLp.getDnsServers().size() == 0)) {
                    try {
                        ArrayList arrayList2 = new ArrayList();
                        arrayList2.add(InetAddress.getByName(NULL_IP));
                        updateLinkPropertyResult.newLp.setDnsServers(arrayList2);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
                this.mLinkProperties = updateLinkPropertyResult.newLp;
                updateTcpBufferSizes(this.mRilRat);
                if (DBG && !updateLinkPropertyResult.oldLp.equals(updateLinkPropertyResult.newLp)) {
                    log("updateLinkProperty old LP=" + updateLinkPropertyResult.oldLp);
                    log("updateLinkProperty new LP=" + updateLinkPropertyResult.newLp);
                }
                if (!(updateLinkPropertyResult.newLp.equals(updateLinkPropertyResult.oldLp) || this.mNetworkAgent == null)) {
                    this.mNetworkAgent.sendLinkProperties(this.mLinkProperties);
                    return updateLinkPropertyResult;
                }
            } else if (DBG) {
                log("updateLinkProperty failed : " + updateLinkPropertyResult.setupResult);
                return updateLinkPropertyResult;
            }
        }
        return updateLinkPropertyResult;
    }
}
