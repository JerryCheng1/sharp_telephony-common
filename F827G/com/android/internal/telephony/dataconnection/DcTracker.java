package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RegistrantList;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.google.android.mms.pdu.CharacterSets;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class DcTracker extends DcTrackerBase {
    private static final String APN_DCM_SPMODE = "spmode.ne.jp";
    private static final String APN_DCM_TEST = "test.net";
    static final String APN_ID = "apn_id";
    private static final int EVENT_3GPP_RECORDS_LOADED = 100;
    private static final String OEM_DNS_PRIMARY = "oem_dns_primary";
    private static final String OEM_DNS_SECOUNDARY = "oem_dns_secoundary";
    private static final int POLL_PDP_MILLIS = 5000;
    static final Uri PREFERAPN_NO_UPDATE_URI = Uri.parse("content://telephony/carriers/preferapn_no_update");
    static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID = Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    private static final String PROPERTY_CDMA_IPPROTOCOL = SystemProperties.get("persist.telephony.cdma.protocol", "IP");
    private static final String PROPERTY_CDMA_ROAMING_IPPROTOCOL = SystemProperties.get("persist.telephony.cdma.rproto", "IP");
    private static final int PROVISIONING_SPINNER_TIMEOUT_MILLIS = 120000;
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";
    protected final String LOG_TAG;
    private ApnChangeObserver mApnObserver;
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private CdmaApnProfileTracker mOmhApt;
    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private ProgressDialog mProvisioningSpinner;
    private long mSubId;
    private ArrayList<Message> mDisconnectAllCompleteMsgList = new ArrayList<>();
    private RegistrantList mAllDataDisconnectedRegistrants = new RegistrantList();
    protected int mDisconnectPendingCount = 0;
    private boolean mReregisterOnReconnectFailure = false;
    private int RetryEnableApn = 0;
    protected boolean mOosIsDisconnect = SystemProperties.getBoolean(PhoneBase.PROPERTY_OOS_IS_DISCONNECT, true);
    private boolean mCanSetPreferApn = false;
    private AtomicBoolean mAttached = new AtomicBoolean(false);
    public boolean mImsRegistrationState = false;
    private ApnContext mWaitCleanUpApnContext = null;
    private boolean mDeregistrationAlarmState = false;
    private PendingIntent mImsDeregistrationDelayIntent = null;
    private boolean mWwanIwlanCoexistFlag = false;
    private ApnSetting oldPreferredApn = null;
    Handler mSimRecordsLoadedHandler = new Handler() { // from class: com.android.internal.telephony.dataconnection.DcTracker.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (!DcTracker.this.mPhone.mIsTheCurrentActivePhone || DcTracker.this.mIsDisposed) {
                DcTracker.this.loge("Sim handler handleMessage: Ignore msgs since phone is inactive");
                return;
            }
            switch (msg.what) {
                case 100:
                    DcTracker.this.log("EVENT_3GPP_RECORDS_LOADED");
                    DcTracker.this.onSimRecordsLoaded();
                    return;
                default:
                    return;
            }
        }
    };

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class ApnChangeObserver extends ContentObserver {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public ApnChangeObserver() {
            super(r2.mDataConnectionTracker);
            DcTracker.this = r2;
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270355));
        }
    }

    public DcTracker(PhoneBase p) {
        super(p);
        if (p.getPhoneType() == 1) {
            this.LOG_TAG = "GsmDCT";
        } else if (p.getPhoneType() == 2) {
            this.LOG_TAG = "CdmaDCT";
        } else {
            this.LOG_TAG = "DCT";
            loge("unexpected phone type [" + p.getPhoneType() + "]");
        }
        log(this.LOG_TAG + ".constructor");
        if (p.getPhoneType() == 2) {
            boolean fetchApnFromOmhCard = p.getContext().getResources().getBoolean(17957036);
            log(this.LOG_TAG + " fetchApnFromOmhCard: " + fetchApnFromOmhCard);
            if (fetchApnFromOmhCard) {
                this.mOmhApt = new CdmaApnProfileTracker((CDMAPhone) p);
                this.mOmhApt.registerForModemProfileReady(this, 270379, null);
            }
        }
        this.mDataConnectionTracker = this;
        registerForAllEvents();
        update();
        this.mApnObserver = new ApnChangeObserver();
        p.getContext().getContentResolver().registerContentObserver(Telephony.Carriers.CONTENT_URI, true, this.mApnObserver);
        initApnContexts();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.internal.telephony.data-reconnect." + apnContext.getApnType());
            filter.addAction("com.android.internal.telephony.data-restart-trysetup." + apnContext.getApnType());
            this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        }
        initEmergencyApnSetting();
        addEmergencyApnSetting();
        this.mProvisionActionName = "com.android.internal.telephony.PROVISION" + p.getPhoneId();
    }

    protected void registerForAllEvents() {
        this.mPhone.mCi.registerForAvailable(this, 270337, null);
        this.mPhone.mCi.registerForOffOrNotAvailable(this, 270342, null);
        this.mPhone.mCi.registerForWwanIwlanCoexistence(this, 270380, null);
        this.mPhone.mCi.registerForDataNetworkStateChanged(this, 270340, null);
        this.mPhone.getCallTracker().registerForVoiceCallEnded(this, 270344, null);
        this.mPhone.getCallTracker().registerForVoiceCallStarted(this, 270343, null);
        this.mPhone.getServiceStateTracker().registerForDataConnectionAttached(this, 270352, null);
        this.mPhone.getServiceStateTracker().registerForDataConnectionDetached(this, 270345, null);
        this.mPhone.getServiceStateTracker().registerForDataRoamingOn(this, 270347, null);
        this.mPhone.getServiceStateTracker().registerForDataRoamingOff(this, 270348, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this, 270358, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this, 270359, null);
        this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 270377, null);
        if (this.mPhone.getPhoneType() == 2) {
            this.mCdmaSsm = CdmaSubscriptionSourceManager.getInstance(this.mPhone.getContext(), this.mPhone.mCi, this, 270357, null);
            sendMessage(obtainMessage(270357));
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void dispose() {
        log("DcTracker.dispose");
        if (this.mProvisionBroadcastReceiver != null) {
            this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
            this.mProvisionBroadcastReceiver = null;
        }
        if (this.mProvisioningSpinner != null) {
            this.mProvisioningSpinner.dismiss();
            this.mProvisioningSpinner = null;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        cleanUpAllConnections(true, (String) null);
        super.dispose();
        this.mPhone.getContext().getContentResolver().unregisterContentObserver(this.mApnObserver);
        this.mApnContexts.clear();
        this.mPrioritySortedApnContexts.clear();
        if (this.mCdmaSsm != null) {
            this.mCdmaSsm.dispose(this);
        }
        if (this.mOmhApt != null) {
            this.mOmhApt.unregisterForModemProfileReady(this);
        }
        unregisterForAllEvents();
        destroyDataConnections();
    }

    protected void unregisterForAllEvents() {
        this.mPhone.mCi.unregisterForAvailable(this);
        this.mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
            this.mIccRecords.set(null);
        }
        IccRecords r2 = (IccRecords) this.mSimRecords.get();
        if (r2 != null) {
            r2.unregisterForRecordsLoaded(this.mSimRecordsLoadedHandler);
            this.mSimRecords.set(null);
        }
        this.mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataRoamingOn(this);
        this.mPhone.getServiceStateTracker().unregisterForDataRoamingOff(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void incApnRefCount(String name) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(name);
        if (apnContext != null) {
            apnContext.incRefCount();
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void decApnRefCount(String name) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(name);
        if (apnContext != null) {
            apnContext.decRefCount();
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isApnSupported(String name) {
        if (name == null) {
            loge("isApnSupported: name=null");
            return false;
        } else if (((ApnContext) this.mApnContexts.get(name)) != null) {
            return true;
        } else {
            loge("Request for unsupported mobile name: " + name);
            return false;
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public int getApnPriority(String name) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
        }
        return apnContext.priority;
    }

    public void setRadio(boolean on) {
        try {
            ITelephony.Stub.asInterface(ServiceManager.checkService("phone")).setRadio(on);
        } catch (Exception e) {
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String provisionUrl, String networkOperator) {
            DcTracker.this = r1;
            this.mNetworkOperator = networkOperator;
            this.mProvisionUrl = provisionUrl;
        }

        private void setEnableFailFastMobileData(int enabled) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270372, enabled, 0));
        }

        private void enableMobileProvisioning() {
            Message msg = DcTracker.this.obtainMessage(270373);
            msg.setData(Bundle.forPair("provisioningUrl", this.mProvisionUrl));
            DcTracker.this.sendMessage(msg);
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            DcTracker.this.mProvisioningSpinner = new ProgressDialog(context);
            DcTracker.this.mProvisioningSpinner.setTitle(this.mNetworkOperator);
            DcTracker.this.mProvisioningSpinner.setMessage(context.getText(17040983));
            DcTracker.this.mProvisioningSpinner.setIndeterminate(true);
            DcTracker.this.mProvisioningSpinner.setCancelable(true);
            DcTracker.this.mProvisioningSpinner.getWindow().setType(2009);
            DcTracker.this.mProvisioningSpinner.show();
            DcTracker.this.sendMessageDelayed(DcTracker.this.obtainMessage(270378, DcTracker.this.mProvisioningSpinner), 120000L);
            DcTracker.this.setRadio(true);
            setEnableFailFastMobileData(1);
            enableMobileProvisioning();
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isApnTypeActive(String type) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(type);
        return (apnContext == null || apnContext.getDcAc() == null) ? false : true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isOnDemandDataPossible(String apnType) {
        boolean apnTypePossible;
        boolean flag;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnContextIsEnabled = apnContext.isEnabled();
        DctConstants.State apnContextState = apnContext.getState();
        if (!apnContextIsEnabled || apnContextState != DctConstants.State.FAILED) {
            apnTypePossible = true;
        } else {
            apnTypePossible = false;
        }
        boolean userDataEnabled = this.mUserDataEnabled;
        if ("mms".equals(apnType)) {
            boolean mobileDataOffOveride = this.mPhone.getContext().getResources().getBoolean(17957024);
            log("isOnDemandDataPossible MobileDataEnabled override = " + mobileDataOffOveride);
            if (this.mUserDataEnabled || mobileDataOffOveride) {
                userDataEnabled = true;
            } else {
                userDataEnabled = false;
            }
        }
        if (!apnTypePossible || !userDataEnabled) {
            flag = false;
        } else {
            flag = true;
        }
        log("isOnDemandDataPossible, possible =" + flag + ", apnContext = " + apnContext);
        return flag;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isDataPossible(String apnType) {
        boolean apnTypePossible;
        boolean dataAllowed;
        boolean possible;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnContextIsEnabled = apnContext.isEnabled();
        DctConstants.State apnContextState = apnContext.getState();
        if (!apnContextIsEnabled || apnContextState != DctConstants.State.FAILED) {
            apnTypePossible = true;
        } else {
            apnTypePossible = false;
        }
        if (apnContext.getApnType().equals("emergency") || isDataAllowed()) {
            dataAllowed = true;
        } else {
            dataAllowed = false;
        }
        if (!dataAllowed || !apnTypePossible) {
            possible = false;
        } else {
            possible = true;
        }
        if ((apnContext.getApnType().equals("default") || apnContext.getApnType().equals("ia")) && this.mPhone.getServiceState().getRilDataRadioTechnology() == 18 && !this.mWwanIwlanCoexistFlag) {
            log("Default data call activation not possible in iwlan.");
            possible = false;
        }
        return possible;
    }

    protected void finalize() {
        log("finalize");
    }

    protected void supplyMessenger() {
        ConnectivityManager cm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        cm.supplyMessenger(0, new Messenger(this));
        cm.supplyMessenger(2, new Messenger(this));
        cm.supplyMessenger(3, new Messenger(this));
        cm.supplyMessenger(4, new Messenger(this));
        cm.supplyMessenger(5, new Messenger(this));
        cm.supplyMessenger(10, new Messenger(this));
        cm.supplyMessenger(11, new Messenger(this));
        cm.supplyMessenger(12, new Messenger(this));
        cm.supplyMessenger(15, new Messenger(this));
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(this.mPhone.getContext(), type, this.LOG_TAG, networkConfig, this);
        this.mApnContexts.put(type, apnContext);
        this.mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    protected void initApnContexts() {
        ApnContext apnContext;
        log("initApnContexts: E");
        for (String networkConfigString : this.mPhone.getContext().getResources().getStringArray(17235981)) {
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            switch (networkConfig.type) {
                case 0:
                    apnContext = addApnContext("default", networkConfig);
                    break;
                case 1:
                case 6:
                case 7:
                case 8:
                case 9:
                case 13:
                default:
                    log("initApnContexts: skipping unknown type=" + networkConfig.type);
                    continue;
                case 2:
                    apnContext = addApnContext("mms", networkConfig);
                    break;
                case 3:
                    apnContext = addApnContext("supl", networkConfig);
                    break;
                case 4:
                    apnContext = addApnContext("dun", networkConfig);
                    break;
                case 5:
                    apnContext = addApnContext("hipri", networkConfig);
                    break;
                case 10:
                    apnContext = addApnContext("fota", networkConfig);
                    break;
                case 11:
                    apnContext = addApnContext("ims", networkConfig);
                    break;
                case 12:
                    apnContext = addApnContext("cbs", networkConfig);
                    break;
                case 14:
                    apnContext = addApnContext("ia", networkConfig);
                    break;
                case 15:
                    apnContext = addApnContext("emergency", networkConfig);
                    break;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }
        log("initApnContexts: X mApnContexts=" + this.mApnContexts);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public LinkProperties getLinkProperties(String apnType) {
        DcAsyncChannel dcac;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null || (dcac = apnContext.getDcAc()) == null) {
            log("return new LinkProperties");
            return new LinkProperties();
        }
        log("return link properites for " + apnType);
        return dcac.getLinkPropertiesSync();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        DcAsyncChannel dataConnectionAc;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null || (dataConnectionAc = apnContext.getDcAc()) == null) {
            log("return new NetworkCapabilities");
            return new NetworkCapabilities();
        }
        log("get active pdp is not null, return NetworkCapabilities for " + apnType);
        return dataConnectionAc.getNetworkCapabilitiesSync();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public String[] getActiveApnTypes() {
        log("get all active apn types");
        ArrayList<String> result = new ArrayList<>();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }
        return (String[]) result.toArray(new String[0]);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public String getActiveApnString(String apnType) {
        ApnSetting apnSetting;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null || (apnSetting = apnContext.getApnSetting()) == null) {
            return null;
        }
        return apnSetting.apn;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isApnTypeEnabled(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        return apnContext.isEnabled();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void setState(DctConstants.State s) {
        log("setState should not be used in GSM" + s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public DctConstants.State getState(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        return apnContext != null ? apnContext.getState() : DctConstants.State.FAILED;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public DctConstants.State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true;
        boolean isAnyEnabled = false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext.getState().ordinal()]) {
                    case 1:
                    case 2:
                        log("overall state is CONNECTED");
                        return DctConstants.State.CONNECTED;
                    case 3:
                    case 4:
                        isConnecting = true;
                        isFailed = false;
                        continue;
                    case 5:
                    case 6:
                        isFailed = false;
                        continue;
                    default:
                        isAnyEnabled = true;
                        continue;
                }
            }
        }
        if (!isAnyEnabled) {
            log("overall state is IDLE");
            return DctConstants.State.IDLE;
        } else if (isConnecting) {
            log("overall state is CONNECTING");
            return DctConstants.State.CONNECTING;
        } else if (!isFailed) {
            log("overall state is IDLE");
            return DctConstants.State.IDLE;
        } else {
            log("overall state is FAILED");
            return DctConstants.State.FAILED;
        }
    }

    /* renamed from: com.android.internal.telephony.dataconnection.DcTracker$2 */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isApnTypeAvailable(String type) {
        if (type.equals("dun") && fetchDunApn() != null) {
            return true;
        }
        if (this.mAllApnSettings != null) {
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                if (((ApnSetting) i$.next()).canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean getAnyDataEnabled() {
        boolean z = false;
        synchronized (this.mDataEnabledLock) {
            if (this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mUserDataEnabledDun) {
                Iterator i$ = this.mApnContexts.values().iterator();
                while (true) {
                    if (i$.hasNext()) {
                        if (isDataAllowed((ApnContext) i$.next())) {
                            z = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return z;
    }

    public boolean getAnyDataEnabled(boolean checkUserDataEnabled) {
        boolean z = false;
        synchronized (this.mDataEnabledLock) {
            if (this.mInternalDataEnabled && ((!checkUserDataEnabled || this.mUserDataEnabled) && ((!checkUserDataEnabled || sPolicyDataEnabled) && this.mUserDataEnabledDun))) {
                Iterator i$ = this.mApnContexts.values().iterator();
                while (true) {
                    if (i$.hasNext()) {
                        if (isDataAllowed((ApnContext) i$.next())) {
                            z = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return z;
    }

    private boolean isDataAllowed(ApnContext apnContext) {
        if ((!apnContext.getApnType().equals("default") && !apnContext.getApnType().equals("ia")) || this.mPhone.getServiceState().getRilDataRadioTechnology() != 18 || this.mWwanIwlanCoexistFlag) {
            return apnContext.isReady() && isDataAllowed();
        }
        log("Default data call activation not allowed in iwlan.");
        return false;
    }

    protected void onDataConnectionDetached() {
        log("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
        this.mAttached.set(false);
    }

    private void onDataConnectionAttached() {
        log("onDataConnectionAttached");
        this.mAttached.set(true);
        if (getOverallState() == DctConstants.State.CONNECTED) {
            log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(false);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        if (this.mAutoAttachOnCreationConfig) {
            this.mAutoAttachOnCreation = true;
        }
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void setupDataAfterDdsSwitchIfPossible() {
        log("setupDataAfterDdsSwitchIfPossible: Attached = " + this.mAttached.get());
        if (this.mAttached.get()) {
            Iterator i$ = this.mPrioritySortedApnContexts.iterator();
            while (i$.hasNext()) {
                ApnContext apnContext = (ApnContext) i$.next();
                if (apnContext.isConnectable()) {
                    log("setupDataAfterDdsSwitchIfPossible: connectable apnContext " + apnContext);
                    if (apnContext.getApnType().equals("default")) {
                        if (apnContext.getReconnectIntent() == null) {
                            log("setupDataAfterDdsSwitchIfPossible: setupDataOnConnectableApns");
                            setupDataOnConnectableApns("Ondemand-DDS-switch");
                        } else {
                            log("setupDataAfterDdsSwitchIfPossible:reconnect timer already running");
                        }
                    }
                }
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isDataAllowed() {
        boolean internalDataEnabled;
        synchronized (this.mDataEnabledLock) {
            internalDataEnabled = this.mInternalDataEnabled;
        }
        boolean attachedState = this.mAttached.get();
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        if (this.mPhone.getServiceState().getRilDataRadioTechnology() == 18 && !desiredPowerState) {
            desiredPowerState = true;
            attachedState = true;
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        boolean recordsLoaded = false;
        if (r != null) {
            recordsLoaded = r.getRecordsLoaded();
            log("isDataAllowed getRecordsLoaded=" + recordsLoaded);
        }
        boolean subscriptionFromNv = isNvSubscription();
        int dataSub = SubscriptionManager.getDefaultDataSubId();
        boolean defaultDataSelected = SubscriptionManager.isValidSubscriptionId(dataSub);
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        if (this.mPhone.getCallTracker() != null) {
            state = this.mPhone.getCallTracker().getState();
        }
        boolean allowed = (attachedState || (this.mAutoAttachOnCreation && this.mPhone.getSubId() == dataSub)) && (subscriptionFromNv || recordsLoaded) && ((state == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) && internalDataEnabled && defaultDataSelected && ((!this.mPhone.getServiceState().getDataRoaming() || getDataOnRoamingEnabled()) && !this.mIsPsRestricted && desiredPowerState));
        if (!allowed) {
            String reason = "";
            if (!attachedState && !this.mAutoAttachOnCreation) {
                reason = reason + " - Attached= " + attachedState;
            }
            if (!subscriptionFromNv && !recordsLoaded) {
                reason = reason + " - SIM not loaded and not NV subscription";
            }
            if (state != PhoneConstants.State.IDLE && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                reason = (reason + " - PhoneState= " + state) + " - Concurrent voice and data not allowed";
            }
            if (!internalDataEnabled) {
                reason = reason + " - mInternalDataEnabled= false";
            }
            if (!defaultDataSelected) {
                reason = reason + " - defaultDataSelected= false";
            }
            if (this.mPhone.getServiceState().getDataRoaming() && !getDataOnRoamingEnabled()) {
                reason = reason + " - Roaming and data roaming not enabled";
            }
            if (this.mIsPsRestricted) {
                reason = reason + " - mIsPsRestricted= true";
            }
            if (!desiredPowerState) {
                reason = reason + " - desiredPowerState= false";
            }
            log("isDataAllowed: not allowed due to" + reason);
        }
        return allowed;
    }

    private void setupDataOnConnectableApns(String reason) {
        log("setupDataOnConnectableApns: " + reason);
        Iterator i$ = this.mPrioritySortedApnContexts.iterator();
        while (i$.hasNext()) {
            ApnContext apnContext = (ApnContext) i$.next();
            log("setupDataOnConnectableApns: apnContext " + apnContext);
            if (apnContext.getState() == DctConstants.State.FAILED) {
                if (TelBrand.IS_DCM) {
                    log("apnContext.getWaitingApnsPermFailCount(): " + apnContext.getWaitingApnsPermFailCount());
                    if (Phone.REASON_VOICE_CALL_ENDED.equals(reason) && apnContext.getWaitingApnsPermFailCount() == 0) {
                    }
                }
                apnContext.setState(DctConstants.State.IDLE);
            }
            if (apnContext.isConnectable()) {
                log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                apnContext.setReason(reason);
                trySetupData(apnContext);
            }
        }
    }

    private boolean trySetupData(ApnContext apnContext) {
        log("trySetupData for type:" + apnContext.getApnType() + " due to " + apnContext.getReason() + " apnContext=" + apnContext);
        log("trySetupData with mIsPsRestricted=" + this.mIsPsRestricted);
        if (TelBrand.IS_DCM) {
            String decryptState = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + decryptState);
            if (decryptState.equals("trigger_restart_min_framework")) {
                log("Stop trySetupData while phone in de-encrypt view !!!");
                return false;
            }
        }
        if (this.mPhone.getSimulatedRadioControl() != null) {
            apnContext.setState(DctConstants.State.CONNECTED);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }
        boolean isEmergencyApn = apnContext.getApnType().equals("emergency");
        this.mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean checkUserDataEnabled = !apnContext.getApnType().equals("ims");
        if (apnContext.getApnType().equals("mms")) {
            checkUserDataEnabled = checkUserDataEnabled && !this.mPhone.getContext().getResources().getBoolean(17957024);
        }
        if (!apnContext.isConnectable() || (!isEmergencyApn && (!isDataAllowed(apnContext) || !getAnyDataEnabled(checkUserDataEnabled) || isEmergency()))) {
            if (TelBrand.IS_KDDI && (apnContext.getState() == DctConstants.State.IDLE || apnContext.getState() == DctConstants.State.SCANNING)) {
                if (this.mEnableApnCarNavi && this.mPhone.getServiceState().getRadioTechnology() == 0) {
                    sendOemKddiFailCauseBroadcast(DcFailCause.SIGNAL_LOST, apnContext);
                    changeMode(false, "", "", "", 0, "", "", "", "");
                    this.mEnableApnCarNavi = false;
                } else if (this.mState != DctConstants.State.DISCONNECTING) {
                    sendOemKddiFailCauseBroadcast(DcFailCause.CUST_NOT_READY_FOR_DATA, apnContext);
                }
            }
            if (!apnContext.getApnType().equals("default") && apnContext.isConnectable()) {
                this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
            }
            notifyOffApnsOfAvailability(apnContext.getReason());
            log("trySetupData: X apnContext not 'ready' retValue=false");
            return false;
        }
        if (apnContext.getState() == DctConstants.State.FAILED) {
            log("trySetupData: make a FAILED ApnContext IDLE so its reusable");
            apnContext.setState(DctConstants.State.IDLE);
        }
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        if (apnContext.getState() == DctConstants.State.IDLE) {
            ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType(), radioTech);
            if (waitingApns.isEmpty()) {
                notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                notifyOffApnsOfAvailability(apnContext.getReason());
                log("trySetupData: X No APN found retValue=false");
                return false;
            }
            apnContext.setWaitingApns(waitingApns);
            log("trySetupData: Create from mAllApnSettings : " + apnListToString(this.mAllApnSettings));
        }
        log("trySetupData: call setupData, waitingApns : " + apnListToString(apnContext.getWaitingApns()));
        boolean retValue = setupData(apnContext, radioTech);
        notifyOffApnsOfAvailability(apnContext.getReason());
        log("trySetupData: X retValue=" + retValue);
        return retValue;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if ((!this.mAttached.get() && this.mOosIsDisconnect) || !apnContext.isReady()) {
                this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getApnType(), PhoneConstants.DataState.DISCONNECTED);
            }
        }
    }

    protected boolean cleanUpAllConnections(boolean tearDown, String reason) {
        log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        boolean specificdisable = false;
        if (!TextUtils.isEmpty(reason)) {
            specificdisable = reason.equals(Phone.REASON_DATA_SPECIFIC_DISABLED);
        }
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                didDisconnect = true;
            }
            if (!specificdisable) {
                apnContext.setReason(reason);
                cleanUpConnection(tearDown, apnContext);
            } else if (!apnContext.getApnType().equals("ims")) {
                log("ApnConextType: " + apnContext.getApnType());
                apnContext.setReason(reason);
                cleanUpConnection(tearDown, apnContext);
            }
        }
        stopDataStallAlarm();
        this.mRequestedApnType = "default";
        log("cleanUpConnection: mDisconnectPendingCount = " + this.mDisconnectPendingCount);
        if (tearDown && this.mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
        return didDisconnect;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    protected void cleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (apnContext == null) {
            log("cleanUpConnection: apn context is null");
            return;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        log("cleanUpConnection: E tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext);
        if (!tearDown) {
            if (dcac != null) {
                dcac.reqReset();
            }
            apnContext.setState(DctConstants.State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        } else if (apnContext.isDisconnected()) {
            apnContext.setState(DctConstants.State.IDLE);
            if (!apnContext.isReady()) {
                if (dcac != null) {
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
            }
        } else if (dcac == null) {
            apnContext.setState(DctConstants.State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        } else if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
            boolean disconnectAll = false;
            if ("dun".equals(apnContext.getApnType()) && teardownForDun()) {
                log("tearing down dedicated DUN connection");
                disconnectAll = true;
            }
            log("cleanUpConnection: tearing down" + (disconnectAll ? " all" : ""));
            Message msg = obtainMessage(270351, apnContext);
            if (disconnectAll) {
                apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
            } else {
                apnContext.getDcAc().tearDown(apnContext, apnContext.getReason(), msg);
            }
            apnContext.setState(DctConstants.State.DISCONNECTING);
            this.mDisconnectPendingCount++;
        }
        if (this.mOmhApt != null) {
            this.mOmhApt.clearActiveApnProfile();
        }
        if (dcac != null) {
            cancelReconnectAlarm(apnContext);
        }
        setupDataForSinglePdnArbitration(apnContext.getReason());
        log("cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
    }

    protected void setupDataForSinglePdnArbitration(String reason) {
        log("setupDataForSinglePdn: reason = " + reason + " isDisconnected = " + isDisconnected());
        if (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isDisconnected() && !Phone.REASON_SINGLE_PDN_ARBITRATION.equals(reason) && !Phone.REASON_RADIO_TURNED_OFF.equals(reason)) {
            setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
        }
    }

    private boolean teardownForDun() {
        return ServiceState.isCdma(this.mPhone.getServiceState().getRilDataRadioTechnology()) || fetchDunApn() != null;
    }

    private void cancelReconnectAlarm(ApnContext apnContext) {
        PendingIntent intent;
        if (apnContext != null && (intent = apnContext.getReconnectIntent()) != null) {
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(intent);
            apnContext.setReconnectIntent(null);
        }
    }

    private String[] parseTypes(String types) {
        return (types == null || types.equals("")) ? new String[]{CharacterSets.MIMENAME_ANY_CHARSET} : types.split(",");
    }

    private boolean imsiMatches(String imsiDB, String imsiSIM) {
        int len = imsiDB.length();
        if (len <= 0 || len > imsiSIM.length()) {
            return false;
        }
        for (int idx = 0; idx < len; idx++) {
            char c = imsiDB.charAt(idx);
            if (!(c == 'x' || c == 'X' || c == imsiSIM.charAt(idx))) {
                return false;
            }
        }
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean mvnoMatches(IccRecords r, String mvnoType, String mvnoMatchData) {
        if (mvnoType.equalsIgnoreCase("spn")) {
            if (r.getServiceProviderName() != null && r.getServiceProviderName().equalsIgnoreCase(mvnoMatchData)) {
                return true;
            }
        } else if (mvnoType.equalsIgnoreCase("imsi")) {
            String imsiSIM = r.getIMSI();
            if (imsiSIM != null && imsiMatches(mvnoMatchData, imsiSIM)) {
                return true;
            }
        } else if (mvnoType.equalsIgnoreCase("gid")) {
            String gid1 = r.getGid1();
            int mvno_match_data_length = mvnoMatchData.length();
            if (gid1 != null && gid1.length() >= mvno_match_data_length && gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvnoMatchData)) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isPermanentFail(DcFailCause dcFailCause) {
        return dcFailCause.isPermanentFail() && (!this.mAttached.get() || dcFailCause != DcFailCause.SIGNAL_LOST);
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        ApnSetting apn = new ApnSetting(cursor.getInt(cursor.getColumnIndexOrThrow("_id")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)), cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)), parseTypes(cursor.getString(cursor.getColumnIndexOrThrow("type"))), cursor.getString(cursor.getColumnIndexOrThrow("protocol")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.ROAMING_PROTOCOL)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.CARRIER_ENABLED)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROFILE_ID)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MODEM_COGNITIVE)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.WAIT_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow("mtu")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE)), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA)));
        if (TelBrand.IS_KDDI) {
            String[] dnses = new String[2];
            try {
                dnses[0] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_primary"));
                dnses[1] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_secoundary"));
            } catch (Exception e) {
                dnses[0] = "";
                dnses[1] = "";
            }
            apn.oemDnses = dnses;
        }
        return apn;
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x0022  */
    /* JADX WARN: Removed duplicated region for block: B:20:0x0054  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private java.util.ArrayList<com.android.internal.telephony.dataconnection.ApnSetting> createApnList(android.database.Cursor r7, com.android.internal.telephony.uicc.IccRecords r8) {
        /*
            r6 = this;
            java.util.ArrayList r1 = new java.util.ArrayList
            r1.<init>()
            java.util.ArrayList r2 = new java.util.ArrayList
            r2.<init>()
            boolean r4 = r7.moveToFirst()
            if (r4 == 0) goto L_0x001c
        L_0x0010:
            com.android.internal.telephony.dataconnection.ApnSetting r0 = r6.makeApnSetting(r7)
            if (r0 != 0) goto L_0x003a
        L_0x0016:
            boolean r4 = r7.moveToNext()
            if (r4 != 0) goto L_0x0010
        L_0x001c:
            boolean r4 = r2.isEmpty()
            if (r4 == 0) goto L_0x0054
            r3 = r1
        L_0x0023:
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            java.lang.String r5 = "createApnList: X result="
            java.lang.StringBuilder r4 = r4.append(r5)
            java.lang.StringBuilder r4 = r4.append(r3)
            java.lang.String r4 = r4.toString()
            r6.log(r4)
            return r3
        L_0x003a:
            boolean r4 = r0.hasMvnoParams()
            if (r4 == 0) goto L_0x0050
            if (r8 == 0) goto L_0x0016
            java.lang.String r4 = r0.mvnoType
            java.lang.String r5 = r0.mvnoMatchData
            boolean r4 = r6.mvnoMatches(r8, r4, r5)
            if (r4 == 0) goto L_0x0016
            r2.add(r0)
            goto L_0x0016
        L_0x0050:
            r1.add(r0)
            goto L_0x0016
        L_0x0054:
            r3 = r2
            goto L_0x0023
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTracker.createApnList(android.database.Cursor, com.android.internal.telephony.uicc.IccRecords):java.util.ArrayList");
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                log("findFreeDataConnection: found free DataConnection= dcac=" + dcac);
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {
        ApnSetting dcacApnSetting;
        log("setupData: apnContext=" + apnContext);
        DcAsyncChannel dcac = null;
        ApnSetting apnSetting = apnContext.getNextWaitingApn();
        if (apnSetting == null) {
            log("setupData: return for no apn found!");
            return false;
        }
        int profileId = apnSetting.profileId;
        if (profileId == 0) {
            profileId = getApnProfileID(apnContext.getApnType());
        }
        if (!((apnContext.getApnType() == "dun" && teardownForDun()) || (dcac = checkForCompatibleConnectedApnContext(apnContext)) == null || (dcacApnSetting = dcac.getApnSettingSync()) == null)) {
            apnSetting = dcacApnSetting;
        }
        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    log("setupData: Higher priority ApnContext active.  Ignoring call");
                    return false;
                } else if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                    log("setupData: Some calls are disconnecting first.  Wait and retry");
                    return false;
                } else {
                    log("setupData: Single pdp. Continue setting up data call.");
                }
            }
            dcac = findFreeDataConnection();
            if (dcac == null) {
                dcac = createDataConnection();
            }
            if (dcac == null) {
                log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting);
        apnContext.setDataConnectionAc(dcac);
        apnContext.setApnSetting(apnSetting);
        apnContext.setState(DctConstants.State.CONNECTING);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        Message msg = obtainMessage();
        msg.what = 270336;
        msg.obj = apnContext;
        dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, radioTech, this.mAutoAttachOnCreation, msg);
        log("setupData: initing!");
        return true;
    }

    private void onApnChanged() {
        log("onApnChanged: tryRestartDataConnections");
        setInitialAttachApn(create3gppApnsList(), (IccRecords) this.mSimRecords.get());
        tryRestartDataConnections(true, Phone.REASON_APN_CHANGED);
    }

    private void tryRestartDataConnections(boolean isCleanupNeeded, String reason) {
        boolean z = true;
        DctConstants.State overallState = getOverallState();
        boolean isDisconnected = overallState == DctConstants.State.IDLE || overallState == DctConstants.State.FAILED;
        if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
        }
        log("tryRestartDataConnections: createAllApnList and cleanUpAllConnections");
        if (TelBrand.IS_DCM && this.mPreferredApn != null) {
            this.oldPreferredApn = this.mPreferredApn;
        } else if (TelBrand.IS_DCM && this.mPreferredApn == null && this.oldPreferredApn != null && reason.equals(Phone.REASON_SIM_LOADED)) {
            this.mPreferredApn = this.oldPreferredApn;
            setPreferredApn(this.mPreferredApn.id);
        }
        createAllApnList();
        if (TelBrand.IS_KDDI && reason.equals(Phone.REASON_APN_CHANGED) && this.mEnableApnCarNavi && !this.mConnectingApnCarNavi) {
            log("DcTracker mConnectingApnCarNavi = true");
            this.mConnectingApnCarNavi = true;
        }
        setInitialAttachApn();
        if (isCleanupNeeded) {
            if (isDisconnected) {
                z = false;
            }
            cleanUpAllConnections(z, reason);
        }
        if (isDisconnected && this.mPhone.getSubId() == SubscriptionManager.getDefaultDataSubId()) {
            setupDataOnConnectableApns(reason);
        } else if (TelBrand.IS_KDDI && this.mAttached.get()) {
            for (ApnContext apnContext : this.mApnContexts.values()) {
                if (apnContext.isReady() && apnContext.getState() == DctConstants.State.IDLE) {
                    startAlarmForReconnect(getApnDelay(reason), apnContext);
                }
            }
        }
    }

    private void onWwanIwlanCoexistenceDone(AsyncResult ar) {
        if (ar.exception != null) {
            log("onWwanIwlanCoexistenceDone: error = " + ar.exception);
            return;
        }
        byte[] array = (byte[]) ar.result;
        log("onWwanIwlanCoexistenceDone, payload hexdump = " + IccUtils.bytesToHexString(array));
        ByteBuffer oemHookResponse = ByteBuffer.wrap(array);
        oemHookResponse.order(ByteOrder.nativeOrder());
        int resp = oemHookResponse.get();
        log("onWwanIwlanCoexistenceDone: resp = " + resp);
        boolean tempStatus = resp > 0;
        if (this.mWwanIwlanCoexistFlag == tempStatus) {
            log("onWwanIwlanCoexistenceDone: no change in status, ignore.");
            return;
        }
        this.mWwanIwlanCoexistFlag = tempStatus;
        if (this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            log("notifyDataConnection IWLAN_AVAILABLE");
            notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
        }
    }

    private void onModemApnProfileReady() {
        if (this.mState == DctConstants.State.FAILED) {
            cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
        }
        log("OMH: onModemApnProfileReady(): Setting up data call");
        tryRestartDataConnections(false, Phone.REASON_SIM_LOADED);
    }

    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void gotoIdleAndNotifyDataConnection(String reason) {
        log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
        this.mActiveApn = null;
    }

    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        Iterator i$ = this.mPrioritySortedApnContexts.iterator();
        while (i$.hasNext()) {
            ApnContext otherContext = (ApnContext) i$.next();
            if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) {
                return false;
            }
            if (otherContext.isEnabled() && otherContext.getState() != DctConstants.State.FAILED) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        int[] singleDcRats = this.mPhone.getContext().getResources().getIntArray(17236015);
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i = 0; i < singleDcRats.length && !onlySingleDcAllowed; i++) {
                if (rilRadioTech == singleDcRats[i]) {
                    onlySingleDcAllowed = true;
                }
            }
        }
        log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return onlySingleDcAllowed;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void restartRadio() {
        log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        this.mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0")) + 1));
    }

    private boolean retryAfterDisconnected(ApnContext apnContext) {
        if (Phone.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason()) || (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isHigherPriorityApnContextActive(apnContext))) {
            return false;
        }
        return true;
    }

    private void startAlarmForReconnect(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect." + apnType);
        intent.putExtra("reconnect_alarm_extra_reason", apnContext.getReason());
        intent.putExtra("reconnect_alarm_extra_type", apnType);
        int subId = this.mPhone.getSubId();
        intent.putExtra("subscription", subId);
        log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext + " subId=" + subId);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void startAlarmForRestartTrySetup(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-restart-trysetup." + apnType);
        intent.putExtra("restart_trysetup_alarm_extra_type", apnType);
        log("startAlarmForRestartTrySetup: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode, ApnContext apnContext) {
        log("notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFail(lastFailCauseCode) && !apnContext.getApnType().equals("default")) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onRecordsLoaded() {
        log("onRecordsLoaded: createAllApnList");
        this.mAutoAttachOnCreationConfig = this.mPhone.getContext().getResources().getBoolean(17957006);
        if (this.mOmhApt != null) {
            log("OMH: onRecordsLoaded(): calling loadProfiles()");
            this.mOmhApt.loadProfiles();
            if (this.mPhone.mCi.getRadioState().isOn()) {
                log("onRecordsLoaded: notifying data availability");
                notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
                return;
            }
            return;
        }
        log("onRecordsLoaded: createAllApnList");
        if (this.mPhone.mCi.getRadioState().isOn()) {
            log("onRecordsLoaded: notifying data availability");
            notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
        }
        tryRestartDataConnections(false, Phone.REASON_SIM_LOADED);
    }

    private void onNvReady() {
        log("onNvReady");
        createAllApnList();
        setupDataOnConnectableApns(Phone.REASON_NV_READY);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onSetDependencyMet(String apnType, boolean met) {
        ApnContext apnContext;
        if (!"hipri".equals(apnType)) {
            ApnContext apnContext2 = (ApnContext) this.mApnContexts.get(apnType);
            if (apnContext2 == null) {
                loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" + apnType + ", " + met + ")");
                return;
            }
            applyNewState(apnContext2, apnContext2.isEnabled(), met);
            if ("default".equals(apnType) && (apnContext = (ApnContext) this.mApnContexts.get("hipri")) != null) {
                applyNewState(apnContext, apnContext.isEnabled(), met);
            }
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        log("applyNewState(" + apnContext.getApnType() + ", " + enabled + "(" + apnContext.isEnabled() + "), " + met + "(" + apnContext.getDependencyMet() + "))");
        if (apnContext.isReady()) {
            cleanup = true;
            if (enabled && met) {
                switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext.getState().ordinal()]) {
                    case 1:
                    case 2:
                    case 4:
                    case 6:
                        log("applyNewState: 'ready' so return");
                        return;
                    case 3:
                    case 5:
                    case 7:
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        break;
                }
            } else if (met) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
                cleanup = (apnContext.getApnType() == "dun" && teardownForDun()) || apnContext.getApnType() == "mms";
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else if (enabled && met) {
            if (apnContext.isEnabled()) {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
            } else {
                apnContext.setReason(Phone.REASON_DATA_ENABLED);
            }
            if (apnContext.getState() == DctConstants.State.FAILED) {
                apnContext.setState(DctConstants.State.IDLE);
            }
            trySetup = true;
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) {
            cleanUpConnection(true, apnContext);
        }
        if (trySetup) {
            trySetupData(apnContext);
        }
        if (TelBrand.IS_DCM && enabled) {
            startNetStatPoll();
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting dunSetting = null;
        if ("dun".equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext);
        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : this.mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            log("curDcac: " + curDcac);
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (dunSetting != null) {
                    if (dunSetting.equals(apnSetting)) {
                        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                            case 1:
                                log("checkForCompatibleConnectedApnContext: found dun conn=" + curDcac + " curApnCtx=" + curApnCtx);
                                return curDcac;
                            case 2:
                                if (potentialDcac == null) {
                                    potentialDcac = curDcac;
                                    potentialApnCtx = curApnCtx;
                                    break;
                                } else {
                                    continue;
                                }
                            case 3:
                            case 4:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                                continue;
                        }
                    } else {
                        continue;
                    }
                } else if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                    switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                        case 1:
                            log("checkForCompatibleConnectedApnContext: found canHandle conn=" + curDcac + " curApnCtx=" + curApnCtx);
                            return curDcac;
                        case 2:
                            if (potentialDcac == null) {
                                if (apnSetting.equals(apnContext.getNextWaitingApn())) {
                                    potentialDcac = curDcac;
                                    potentialApnCtx = curApnCtx;
                                    break;
                                } else {
                                    break;
                                }
                            } else {
                                continue;
                            }
                        case 3:
                        case 4:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                            continue;
                    }
                }
            }
        }
        if (potentialDcac != null) {
            log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac + " curApnCtx=" + potentialApnCtx);
            return potentialDcac;
        }
        log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onEnableApn(int apnId, int enabled) {
        boolean z = true;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(apnId));
        if (TelBrand.IS_KDDI) {
            if (enabled == 1 && this.RetryEnableApn < 100) {
                for (ApnContext apnContextCheck : this.mApnContexts.values()) {
                    if (apnContextCheck.isEnabled() && apnContextCheck.getState() == DctConstants.State.DISCONNECTING) {
                        log("onEnableApn : " + apnContextCheck.getApnType() + " is Disconnecting!!!");
                        Message msg = obtainMessage(270349);
                        msg.arg1 = apnId;
                        msg.arg2 = enabled;
                        sendMessageDelayed(msg, 200L);
                        this.RetryEnableApn++;
                        return;
                    }
                }
            }
            this.RetryEnableApn = 0;
        }
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }
        log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        if (enabled != 1) {
            z = false;
        }
        applyNewState(apnContext, z, apnContext.getDependencyMet());
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean onTrySetupData(String reason) {
        log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    protected boolean onTrySetupData(ApnContext apnContext) {
        log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRoamingOff() {
        log("onRoamingOff");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (this.mUserDataEnabled) {
            if (!getDataOnRoamingEnabled()) {
                notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
                setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
                return;
            }
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRoamingOn() {
        log("onRoamingOn");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (this.mUserDataEnabled) {
            if (getDataOnRoamingEnabled()) {
                log("onRoamingOn: setup data on roaming");
                setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
                notifyDataConnection(Phone.REASON_ROAMING_ON);
                return;
            }
            log("onRoamingOn: Tear down data connection on roaming.");
            cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
            broadcastDisconnectDun();
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRadioAvailable() {
        log("onRadioAvailable");
        if (this.mPhone.getSimulatedRadioControl() != null) {
            notifyDataConnection(null);
            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }
        if (getOverallState() != DctConstants.State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRadioOffOrNotAvailable() {
        this.mReregisterOnReconnectFailure = false;
        if (this.mPhone.getSimulatedRadioControl() != null) {
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void completeConnection(ApnContext apnContext) {
        apnContext.isProvisioningApn();
        log("completeConnection: successful, notify the world apnContext=" + apnContext);
        if (this.mIsProvisioning && !TextUtils.isEmpty(this.mProvisioningUrl)) {
            log("completeConnection: MOBILE_PROVISIONING_ACTION url=" + this.mProvisioningUrl);
            Intent newIntent = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
            newIntent.setData(Uri.parse(this.mProvisioningUrl));
            newIntent.setFlags(272629760);
            try {
                this.mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        if (this.mProvisioningSpinner != null) {
            sendMessage(obtainMessage(270378, this.mProvisioningSpinner));
        }
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(false);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDataSetupComplete(AsyncResult ar) {
        DcFailCause dcFailCause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            if (ar.exception == null) {
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac == null) {
                    log("onDataSetupComplete: no connection to DC, handle as error");
                    DcFailCause cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                    handleError = true;
                } else {
                    ApnSetting apn = apnContext.getApnSetting();
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                    if (!(apn == null || apn.proxy == null || apn.proxy.length() == 0)) {
                        try {
                            String port = apn.port;
                            if (TextUtils.isEmpty(port)) {
                                port = "8080";
                            }
                            dcac.setLinkPropertiesHttpProxySync(new ProxyInfo(apn.proxy, Integer.parseInt(port), null));
                        } catch (NumberFormatException e) {
                            loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" + apn.port + "): " + e);
                        }
                    }
                    if (TextUtils.equals(apnContext.getApnType(), "default")) {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                        if (this.mCanSetPreferApn && this.mPreferredApn == null) {
                            log("onDataSetupComplete: PREFERED APN is null");
                            this.mPreferredApn = apn;
                            if (this.mPreferredApn != null) {
                                setPreferredApn(this.mPreferredApn.id);
                            }
                        }
                    } else {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    }
                    if (!TelBrand.IS_KDDI || !this.mEnableApnCarNavi || apnContext.getState() != DctConstants.State.DISCONNECTING) {
                        apnContext.setState(DctConstants.State.CONNECTED);
                    } else {
                        log("onDataSetupComplete: type=" + apnContext.getApnType() + " Do not change CONNECTED state during DISCONNECTING state when CarNavi");
                    }
                    boolean isProvApn = apnContext.isProvisioningApn();
                    ConnectivityManager cm = ConnectivityManager.from(this.mPhone.getContext());
                    if (this.mProvisionBroadcastReceiver != null) {
                        this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
                        this.mProvisionBroadcastReceiver = null;
                    }
                    if (!isProvApn || this.mIsProvisioning) {
                        cm.setProvisioningNotificationVisible(false, 0, this.mProvisionActionName);
                        completeConnection(apnContext);
                    } else {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as mIsProvisioning:" + this.mIsProvisioning + " == false && (isProvisioningApn:" + isProvApn + " == true");
                        this.mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(cm.getMobileProvisioningUrl(), TelephonyManager.getDefault().getNetworkOperatorName());
                        this.mPhone.getContext().registerReceiver(this.mProvisionBroadcastReceiver, new IntentFilter(this.mProvisionActionName));
                        cm.setProvisioningNotificationVisible(true, 0, this.mProvisionActionName);
                        setRadio(false);
                        Intent intent = new Intent("android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN");
                        intent.putExtra(Telephony.Carriers.APN, apnContext.getApnSetting().apn);
                        intent.putExtra("apnType", apnContext.getApnType());
                        String apnType = apnContext.getApnType();
                        LinkProperties linkProperties = getLinkProperties(apnType);
                        if (linkProperties != null) {
                            intent.putExtra("linkProperties", linkProperties);
                            String iface = linkProperties.getInterfaceName();
                            if (iface != null) {
                                intent.putExtra("iface", iface);
                            }
                        }
                        NetworkCapabilities networkCapabilities = getNetworkCapabilities(apnType);
                        if (networkCapabilities != null) {
                            intent.putExtra("networkCapabilities", networkCapabilities);
                        }
                        this.mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                    }
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType() + ", reason:" + apnContext.getReason());
                }
            } else {
                DcFailCause cause2 = (DcFailCause) ar.result;
                ApnSetting apn2 = apnContext.getApnSetting();
                Object[] objArr = new Object[2];
                objArr[0] = apn2 == null ? "unknown" : apn2.apn;
                objArr[1] = cause2;
                log(String.format("onDataSetupComplete: error apn=%s cause=%s", objArr));
                if (cause2 == null) {
                    cause2 = DcFailCause.UNKNOWN;
                }
                if (cause2.isEventLoggable()) {
                    EventLog.writeEvent((int) EventLogTags.PDP_SETUP_FAIL, Integer.valueOf(cause2.ordinal()), Integer.valueOf(getCellLocationId()), Integer.valueOf(TelephonyManager.getDefault().getNetworkType()));
                }
                ApnSetting apn3 = apnContext.getApnSetting();
                this.mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(), apnContext.getApnType(), apn3 != null ? apn3.apn : "unknown", cause2.toString());
                if (isPermanentFail(cause2)) {
                    apnContext.decWaitingApnsPermFailCount();
                }
                apnContext.removeWaitingApn(apnContext.getApnSetting());
                log(String.format("onDataSetupComplete: WaitingApns.size=%d WaitingApnsPermFailureCountDown=%d", Integer.valueOf(apnContext.getWaitingApns().size()), Integer.valueOf(apnContext.getWaitingApnsPermFailCount())));
                handleError = true;
            }
            if (handleError) {
                onDataSetupCompleteError(ar);
            }
            if (!this.mInternalDataEnabled) {
                cleanUpAllConnections(null);
                return;
            }
            return;
        }
        throw new RuntimeException("onDataSetupComplete: No apnContext");
    }

    private int getApnDelay(String reason) {
        return (this.mFailFast || Phone.REASON_NW_TYPE_CHANGED.equals(reason) || Phone.REASON_APN_CHANGED.equals(reason)) ? SystemProperties.getInt("persist.radio.apn_ff_delay", 3000) : SystemProperties.getInt("persist.radio.apn_delay", 20000);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDataSetupCompleteError(AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            if (apnContext.getWaitingApns().isEmpty()) {
                apnContext.setState(DctConstants.State.FAILED);
                this.mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());
                apnContext.setDataConnectionAc(null);
                if (apnContext.getWaitingApnsPermFailCount() == 0) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                    return;
                }
                int delay = getApnDelay(Phone.REASON_APN_FAILED);
                log("onDataSetupCompleteError: Not all APN's had permanent failures delay=" + delay);
                startAlarmForRestartTrySetup(delay, apnContext);
                return;
            }
            log("onDataSetupCompleteError: Try next APN");
            apnContext.setState(DctConstants.State.SCANNING);
            startAlarmForReconnect(getApnDelay(Phone.REASON_APN_FAILED), apnContext);
            return;
        }
        throw new RuntimeException("onDataSetupCompleteError: No apnContext");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDisconnectDone(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
            apnContext.setState(DctConstants.State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            boolean allApnDisconnected = true;
            Iterator i$ = this.mApnContexts.values().iterator();
            while (true) {
                if (i$.hasNext()) {
                    if (((ApnContext) i$.next()).getState() == DctConstants.State.CONNECTED) {
                        allApnDisconnected = false;
                        break;
                    }
                } else {
                    break;
                }
            }
            if (allApnDisconnected) {
                this.mIsPhysicalLinkUp = false;
                stopNetStatPoll();
            }
            if (isDisconnected()) {
                if (this.mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                    log("onDisconnectDone: radio will be turned off, no retries");
                    apnContext.setApnSetting(null);
                    apnContext.setDataConnectionAc(null);
                    if (this.mDisconnectPendingCount > 0) {
                        this.mDisconnectPendingCount--;
                    }
                    if (this.mDisconnectPendingCount == 0) {
                        notifyDataDisconnectComplete();
                        notifyAllDataDisconnected();
                        return;
                    }
                    return;
                }
                if (TelBrand.IS_DCM && !this.mUserDataEnabledDun) {
                    if (this.mPhone.getState() != PhoneConstants.State.IDLE) {
                        this.mIsEpcPending = true;
                    } else {
                        updateOemDataSettings();
                    }
                }
                if (TelBrand.IS_DCM && TextUtils.equals(apnContext.getReason(), Phone.REASON_APN_CHANGED)) {
                    createAllApnList();
                    setInitialAttachApn();
                }
            }
            if (!this.mAttached.get() || !apnContext.isReady() || !retryAfterDisconnected(apnContext)) {
                boolean restartRadioAfterProvisioning = this.mPhone.getContext().getResources().getBoolean(17956984);
                if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                    log("onDisconnectDone: restartRadio after provisioning");
                    restartRadio();
                }
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                if (!isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) || Phone.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason())) {
                    log("onDisconnectDone: not retrying");
                } else {
                    log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                    setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
                }
            } else {
                SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                log("onDisconnectDone: attached, ready and retry after disconnect");
                if (!TelBrand.IS_KDDI || !this.mEnableApnCarNavi) {
                    startAlarmForReconnect(getApnDelay(apnContext.getReason()), apnContext);
                } else {
                    startAlarmForReconnect(10000, apnContext);
                }
            }
            if (this.mDisconnectPendingCount > 0) {
                this.mDisconnectPendingCount--;
            }
            if (this.mDisconnectPendingCount == 0) {
                notifyDataDisconnectComplete();
                notifyAllDataDisconnected();
                return;
            }
            return;
        }
        loge("onDisconnectDone: Invalid ar in onDisconnectDone, ignore");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDisconnectDcRetrying(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            apnContext.setState(DctConstants.State.RETRYING);
            log("onDisconnectDcRetrying: apnContext=" + apnContext);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            return;
        }
        loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onVoiceCallStarted() {
        log("onVoiceCallStarted");
        this.mInVoiceCall = true;
        if (isConnected() && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onVoiceCallEnded() {
        log("onVoiceCallEnded");
        if (TelBrand.IS_DCM && this.mIsEpcPending) {
            this.mIsEpcPending = false;
            updateOemDataSettings();
        }
        this.mInVoiceCall = false;
        if (isConnected()) {
            if (!this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else {
                resetPollStats();
            }
        }
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        log("onCleanUpConnection");
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(apnId));
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isConnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getState() == DctConstants.State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isDisconnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                return false;
            }
        }
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void notifyDataConnection(String reason) {
        log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() || !this.mOosIsDisconnect) {
                if (apnContext.isReady()) {
                    log("notifyDataConnection: type:" + apnContext.getApnType());
                    this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getApnType());
                }
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    private boolean isNvSubscription() {
        return this.mCdmaSsm != null && UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology()) == 2 && this.mCdmaSsm.getCdmaSubscriptionSource() == 1;
    }

    private String getOperatorNumeric() {
        String result;
        if (isNvSubscription()) {
            result = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            log("getOperatorNumberic - returning from NV: " + result);
        } else {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            result = r != null ? r.getOperatorNumeric() : "";
            log("getOperatorNumberic - returning from card: " + result);
        }
        return result == null ? "" : result;
    }

    private void createAllApnList() {
        this.mAllApnSettings.clear();
        String operator = getOperatorNumeric();
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        if (!(this.mOmhApt == null || !ServiceState.isCdma(radioTech) || 13 == radioTech)) {
            new ArrayList();
            ArrayList<ApnSetting> mOmhApnsList = this.mOmhApt.getOmhApnProfilesList();
            if (!mOmhApnsList.isEmpty()) {
                log("createAllApnList: Copy Omh profiles");
                this.mAllApnSettings.addAll(mOmhApnsList);
            }
        }
        if (this.mAllApnSettings.isEmpty() && operator != null && !operator.isEmpty()) {
            String selection = ("numeric = '" + operator + "'") + " and carrier_enabled = 1";
            log("createAllApnList: selection=" + selection);
            Cursor cursor = this.mPhone.getContext().getContentResolver().query(Telephony.Carriers.CONTENT_URI, null, selection, null, null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    this.mAllApnSettings = createApnList(cursor, (IccRecords) this.mIccRecords.get());
                }
                cursor.close();
            }
        }
        dedupeApnSettings();
        if (this.mAllApnSettings.isEmpty() && isDummyProfileNeeded()) {
            addDummyApnSettings(operator);
        }
        addEmergencyApnSetting();
        if (this.mAllApnSettings.isEmpty()) {
            log("createAllApnList: No APN found for carrier: " + operator);
            this.mPreferredApn = null;
        } else {
            this.mPreferredApn = getPreferredApn();
            if (this.mPreferredApn != null && !this.mPreferredApn.numeric.equals(operator)) {
                this.mPreferredApn = null;
                setPreferredApn(-1);
            }
            if (TelBrand.IS_DCM && this.mPreferredApn == null && this.oldPreferredApn == null && this.mAllApnSettings.size() > 0) {
                Iterator i$ = this.mAllApnSettings.iterator();
                while (i$.hasNext()) {
                    ApnSetting p = (ApnSetting) i$.next();
                    if ((APN_DCM_SPMODE.equals(p.apn) && p.canHandleType("default") && p.id == 1) || (APN_DCM_TEST.equals(p.apn) && p.canHandleType("default") && p.id == 2)) {
                        this.mPreferredApn = p;
                        setPreferredApn(this.mPreferredApn.id);
                        log("mPreferredApn == null --> mPreferredApn=" + this.mPreferredApn);
                        break;
                    }
                }
            }
            log("createAllApnList: mPreferredApn=" + this.mPreferredApn);
        }
        if (TelBrand.IS_KDDI) {
            modifyOemKddiProfiles(operator);
        }
        log("createAllApnList: X mAllApnSettings=" + this.mAllApnSettings);
        setDataProfilesAsNeeded();
    }

    private ArrayList<ApnSetting> create3gppApnsList() {
        createAllApnList();
        return this.mAllApnSettings;
    }

    private void dedupeApnSettings() {
        new ArrayList();
        for (int i = 0; i < this.mAllApnSettings.size() - 1; i++) {
            ApnSetting first = (ApnSetting) this.mAllApnSettings.get(i);
            int j = i + 1;
            while (j < this.mAllApnSettings.size()) {
                ApnSetting second = (ApnSetting) this.mAllApnSettings.get(j);
                if (apnsSimilar(first, second)) {
                    ApnSetting newApn = mergeApns(first, second);
                    this.mAllApnSettings.set(i, newApn);
                    first = newApn;
                    this.mAllApnSettings.remove(j);
                } else {
                    j++;
                }
            }
        }
    }

    private boolean apnTypeSameAny(ApnSetting first, ApnSetting second) {
        for (int index1 = 0; index1 < first.types.length; index1++) {
            for (int index2 = 0; index2 < second.types.length; index2++) {
                if (first.types[index1].equals(CharacterSets.MIMENAME_ANY_CHARSET) || second.types[index2].equals(CharacterSets.MIMENAME_ANY_CHARSET) || first.types[index1].equals(second.types[index2])) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean apnsSimilar(ApnSetting first, ApnSetting second) {
        return !first.canHandleType("dun") && !second.canHandleType("dun") && Objects.equals(first.apn, second.apn) && !apnTypeSameAny(first, second) && xorEquals(first.proxy, second.proxy) && xorEquals(first.port, second.port) && first.carrierEnabled == second.carrierEnabled && first.bearer == second.bearer && first.profileId == second.profileId && Objects.equals(first.mvnoType, second.mvnoType) && Objects.equals(first.mvnoMatchData, second.mvnoMatchData) && xorEquals(first.mmsc, second.mmsc) && xorEquals(first.mmsProxy, second.mmsProxy) && xorEquals(first.mmsPort, second.mmsPort);
    }

    private boolean xorEquals(String first, String second) {
        return Objects.equals(first, second) || TextUtils.isEmpty(first) || TextUtils.isEmpty(second);
    }

    private ApnSetting mergeApns(ApnSetting dest, ApnSetting src) {
        ArrayList<String> resultTypes = new ArrayList<>();
        resultTypes.addAll(Arrays.asList(dest.types));
        String[] arr$ = src.types;
        for (String srcType : arr$) {
            if (!resultTypes.contains(srcType)) {
                resultTypes.add(srcType);
            }
        }
        return new ApnSetting(dest.id, dest.numeric, dest.carrier, dest.apn, TextUtils.isEmpty(dest.proxy) ? src.proxy : dest.proxy, TextUtils.isEmpty(dest.port) ? src.port : dest.port, TextUtils.isEmpty(dest.mmsc) ? src.mmsc : dest.mmsc, TextUtils.isEmpty(dest.mmsProxy) ? src.mmsProxy : dest.mmsProxy, TextUtils.isEmpty(dest.mmsPort) ? src.mmsPort : dest.mmsPort, dest.user, dest.password, dest.authType, (String[]) resultTypes.toArray(new String[0]), src.protocol.equals("IPV4V6") ? src.protocol : dest.protocol, src.roamingProtocol.equals("IPV4V6") ? src.roamingProtocol : dest.roamingProtocol, dest.carrierEnabled, dest.bearer, dest.profileId, dest.modemCognitive || src.modemCognitive, dest.maxConns, dest.waitTime, dest.maxConnsTime, dest.mtu, dest.mvnoType, dest.mvnoMatchData);
    }

    private boolean isDummyProfileNeeded() {
        int radioTechFam = UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology());
        IccRecords r = (IccRecords) this.mIccRecords.get();
        log("isDummyProfileNeeded: radioTechFam = " + radioTechFam);
        return radioTechFam == 2 || (radioTechFam == -1 && r != null && (r instanceof RuimRecords));
    }

    private void addDummyApnSettings(String operator) {
        log("createAllApnList: Creating dummy apn for cdma operator:" + operator);
        this.mAllApnSettings.add(new ApnSetting(0, operator, null, null, null, null, null, null, null, null, null, 3, new String[]{"default", "mms", "supl", "hipri", "fota", "ims", "cbs"}, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0, 0, false, 0, 0, 0, 0, "", ""));
        this.mAllApnSettings.add(new ApnSetting(3, operator, null, null, null, null, null, null, null, null, null, 3, new String[]{"dun"}, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0, 0, false, 0, 0, 0, 0, "", ""));
    }

    private DcAsyncChannel createDataConnection() {
        log("createDataConnection E");
        int id = this.mUniqueIdGenerator.getAndIncrement();
        DataConnection conn = DataConnection.makeDataConnection(this.mPhone, id, this, this.mDcTesterFailBringUpAll, this.mDcc);
        this.mDataConnections.put(Integer.valueOf(id), conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, this.LOG_TAG);
        int status = dcac.fullyConnectSync(this.mPhone.getContext(), this, conn.getHandler());
        if (status == 0) {
            this.mDataConnectionAcHashMap.put(Integer.valueOf(dcac.getDataConnectionIdSync()), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }
        log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if (this.mDataConnections != null) {
            log("destroyDataConnections: clear mDataConnectionList");
            this.mDataConnections.clear();
            return;
        }
        log("destroyDataConnections: mDataConnecitonList is empty, ignore");
    }

    private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {
        boolean usePreferred;
        ApnSetting dun;
        log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<ApnSetting> apnList = new ArrayList<>();
        if (!requestedApnType.equals("dun") || (dun = fetchDunApn()) == null) {
            String operator = getOperatorNumeric();
            if (TelBrand.IS_DCM || TelBrand.IS_KDDI) {
                if (!TelBrand.IS_KDDI || (this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(requestedApnType))) {
                    if (this.mCanSetPreferApn && this.mPreferredApn != null) {
                        log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                        if (this.mPreferredApn.numeric != null && this.mPreferredApn.numeric.equals(operator) && ((this.mPreferredApn.bearer == 0 || this.mPreferredApn.bearer == radioTech) && this.mPreferredApn.canHandleType(requestedApnType))) {
                            apnList.add(this.mPreferredApn);
                            log("buildWaitingApns: X added preferred apnList=" + apnList);
                        }
                    }
                    log("buildWaitingApns: no preferred APN");
                }
                if (this.mAllApnSettings != null || this.mAllApnSettings.isEmpty()) {
                    loge("mAllApnSettings is empty!");
                } else {
                    log("buildWaitingApns: mAllApnSettings=" + this.mAllApnSettings);
                    Iterator i$ = this.mAllApnSettings.iterator();
                    while (i$.hasNext()) {
                        ApnSetting apn = (ApnSetting) i$.next();
                        log("buildWaitingApns: apn=" + apn);
                        if (!apn.canHandleType(requestedApnType)) {
                            log("buildWaitingApns: couldn't handle requesedApnType=" + requestedApnType);
                        } else if (apn.bearer == 0 || apn.bearer == radioTech) {
                            log("buildWaitingApns: adding apn=" + apn.toString());
                            apnList.add(apn);
                        } else {
                            log("buildWaitingApns: bearer:" + apn.bearer + " != radioTech:" + radioTech);
                        }
                    }
                }
                log("buildWaitingApns: X apnList=" + apnList);
            } else {
                try {
                    usePreferred = !this.mPhone.getContext().getResources().getBoolean(17956983);
                } catch (Resources.NotFoundException e) {
                    log("buildWaitingApns: usePreferred NotFoundException set to true");
                    usePreferred = true;
                }
                log("buildWaitingApns: usePreferred=" + usePreferred + " canSetPreferApn=" + this.mCanSetPreferApn + " mPreferredApn=" + this.mPreferredApn + " operator=" + operator + " radioTech=" + radioTech);
                if (usePreferred && this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(requestedApnType)) {
                    log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                    if (this.mPreferredApn.numeric == null || !this.mPreferredApn.numeric.equals(operator)) {
                        log("buildWaitingApns: no preferred APN");
                        setPreferredApn(-1);
                        this.mPreferredApn = null;
                    } else if (this.mPreferredApn.bearer == 0 || this.mPreferredApn.bearer == radioTech) {
                        apnList.add(this.mPreferredApn);
                        log("buildWaitingApns: X added preferred apnList=" + apnList);
                    } else {
                        log("buildWaitingApns: no preferred APN");
                        setPreferredApn(-1);
                        this.mPreferredApn = null;
                    }
                }
                if (this.mAllApnSettings != null) {
                }
                loge("mAllApnSettings is empty!");
                log("buildWaitingApns: X apnList=" + apnList);
            }
        } else {
            apnList.add(dun);
            log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
        }
        return apnList;
    }

    private String apnListToString(ArrayList<ApnSetting> apns) {
        StringBuilder result = new StringBuilder();
        int size = apns.size();
        for (int i = 0; i < size; i++) {
            result.append('[').append(apns.get(i).toString()).append(']');
        }
        return result.toString();
    }

    private void setPreferredApn(int pos) {
        if (!this.mCanSetPreferApn) {
            log("setPreferredApn: X !canSEtPreferApn");
            return;
        }
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString(this.mPhone.getSubId()));
        log("setPreferredApn: delete");
        ContentResolver resolver = this.mPhone.getContext().getContentResolver();
        resolver.delete(uri, null, null);
        if (pos >= 0) {
            log("setPreferredApn: insert");
            ContentValues values = new ContentValues();
            values.put(APN_ID, Integer.valueOf(pos));
            resolver.insert(uri, values);
        }
    }

    private ApnSetting getPreferredApn() {
        if (this.mAllApnSettings.isEmpty()) {
            log("getPreferredApn: X not found mAllApnSettings.isEmpty");
            return null;
        }
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString(this.mPhone.getSubId())), new String[]{"_id", "name", Telephony.Carriers.APN}, null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            this.mCanSetPreferApn = true;
        } else {
            this.mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + this.mRequestedApnType + " cursor=" + cursor + " cursor.count=" + (cursor != null ? cursor.getCount() : 0));
        if (this.mCanSetPreferApn && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int pos = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                ApnSetting p = (ApnSetting) i$.next();
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(this.mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        log("getPreferredApn: X not found");
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase, android.os.Handler
    public void handleMessage(Message msg) {
        boolean tearDown = false;
        log("handleMessage msg=" + msg);
        if (!this.mPhone.mIsTheCurrentActivePhone || this.mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
            return;
        }
        switch (msg.what) {
            case 270338:
                onRecordsLoaded();
                return;
            case 270339:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext) msg.obj);
                    return;
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String) msg.obj);
                    return;
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                    return;
                }
            case 270345:
                onDataConnectionDetached();
                return;
            case 270352:
                onDataConnectionAttached();
                return;
            case 270354:
                doRecovery();
                return;
            case 270355:
                onApnChanged();
                return;
            case 270357:
            case 270377:
                if (onUpdateIcc()) {
                    log("onUpdateIcc: tryRestartDataConnections nwTypeChanged");
                    tryRestartDataConnections(true, Phone.REASON_NW_TYPE_CHANGED);
                    return;
                } else if (isNvSubscription()) {
                    onNvReady();
                    return;
                } else {
                    return;
                }
            case 270358:
                log("EVENT_PS_RESTRICT_ENABLED " + this.mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                this.mIsPsRestricted = true;
                return;
            case 270359:
                log("EVENT_PS_RESTRICT_DISABLED " + this.mIsPsRestricted);
                this.mIsPsRestricted = false;
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(false);
                    return;
                }
                if (this.mState == DctConstants.State.FAILED) {
                    cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                    this.mReregisterOnReconnectFailure = false;
                }
                ApnContext apnContext = (ApnContext) this.mApnContexts.get("default");
                if (apnContext != null) {
                    apnContext.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                    trySetupData(apnContext);
                    return;
                }
                loge("**** Default ApnContext not found ****");
                if (Build.IS_DEBUGGABLE) {
                    throw new RuntimeException("Default ApnContext not found");
                }
                return;
            case 270360:
                if (msg.arg1 != 0) {
                    tearDown = true;
                }
                log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext) msg.obj);
                    return;
                }
                loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context, call super");
                super.handleMessage(msg);
                return;
            case 270363:
                onSetInternalDataEnabled(msg.arg1 == 1, (Message) msg.obj);
                return;
            case 270365:
                Message mCause = obtainMessage(270365, null);
                if (msg.obj != null && (msg.obj instanceof String)) {
                    mCause.obj = msg.obj;
                }
                super.handleMessage(mCause);
                return;
            case 270378:
                if (this.mProvisioningSpinner == msg.obj) {
                    this.mProvisioningSpinner.dismiss();
                    this.mProvisioningSpinner = null;
                    return;
                }
                return;
            case 270379:
                onModemApnProfileReady();
                return;
            case 270380:
                onWwanIwlanCoexistenceDone((AsyncResult) msg.obj);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    protected int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, "ims")) {
            return 2;
        }
        if (TextUtils.equals(apnType, "fota")) {
            return 3;
        }
        if (TextUtils.equals(apnType, "cbs")) {
            return 4;
        }
        if (TextUtils.equals(apnType, "ia") || !TextUtils.equals(apnType, "dun")) {
            return 0;
        }
        return 1;
    }

    private int getCellLocationId() {
        CellLocation loc = this.mPhone.getCellLocation();
        if (loc == null) {
            return -1;
        }
        if (loc instanceof GsmCellLocation) {
            return ((GsmCellLocation) loc).getCid();
        }
        if (loc instanceof CdmaCellLocation) {
            return ((CdmaCellLocation) loc).getBaseStationId();
        }
        return -1;
    }

    private IccRecords getUiccRecords(int appFamily) {
        return this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), appFamily);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean onUpdateIcc() {
        boolean result = false;
        if (this.mUiccController == null || !SubscriptionManager.isValidSubscriptionId(this.mPhone.getSubId())) {
            loge("onUpdateIcc: mUiccController is null. Error!");
            return false;
        }
        updateSimRecords();
        int appFamily = UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology());
        if (this.mPhone.getPhoneType() == 1) {
            appFamily = 1;
        }
        IccRecords newIccRecords = getUiccRecords(appFamily);
        log("onUpdateIcc: newIccRecords " + (newIccRecords != null ? newIccRecords.getClass().getName() : null));
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != newIccRecords) {
            if (r != null) {
                log("Removing stale icc objects. " + (r != null ? r.getClass().getName() : null));
                r.unregisterForRecordsLoaded(this);
                this.mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                log("New records found " + (newIccRecords != null ? newIccRecords.getClass().getName() : null));
                this.mIccRecords.set(newIccRecords);
                newIccRecords.registerForRecordsLoaded(this, 270338, null);
            }
            result = true;
        }
        return result;
    }

    private void updateSimRecords() {
        if (this.mUiccController != null) {
            IccRecords newSimRecords = getUiccRecords(1);
            log("updateSimRecords: newSimRecords = " + newSimRecords);
            IccRecords r = (IccRecords) this.mSimRecords.get();
            if (r != newSimRecords) {
                if (r != null) {
                    log("Removing stale sim objects.");
                    r.unregisterForRecordsLoaded(this.mSimRecordsLoadedHandler);
                    this.mSimRecords.set(null);
                }
                if (newSimRecords != null) {
                    log("New sim records found");
                    this.mSimRecords.set(newSimRecords);
                    newSimRecords.registerForRecordsLoaded(this.mSimRecordsLoadedHandler, 100, null);
                }
            }
        }
    }

    public void onSimRecordsLoaded() {
        setInitialAttachApn(create3gppApnsList(), (IccRecords) this.mSimRecords.get());
    }

    public void update() {
        boolean z = true;
        log("update sub = " + this.mPhone.getSubId());
        log("update(): Active DDS, register for all events now!");
        onUpdateIcc();
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data" + this.mPhone.getPhoneId(), 1) != 1) {
            z = false;
        }
        this.mUserDataEnabled = z;
        if (this.mPhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mPhone).updateCurrentCarrierInProvider();
            supplyMessenger();
        } else if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
            supplyMessenger();
        } else {
            log("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void cleanUpAllConnections(String cause) {
        cleanUpAllConnections(cause, (Message) null);
    }

    public void updateRecords() {
        onUpdateIcc();
    }

    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        log("cleanUpAllConnections");
        if (disconnectAllCompleteMsg != null) {
            this.mDisconnectAllCompleteMsgList.add(disconnectAllCompleteMsg);
        }
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        Iterator i$ = this.mDisconnectAllCompleteMsgList.iterator();
        while (i$.hasNext()) {
            i$.next().sendToTarget();
        }
        this.mDisconnectAllCompleteMsgList.clear();
    }

    protected void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        this.mFailFast = false;
        this.mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        this.mAllDataDisconnectedRegistrants.addUnique(h, what, obj);
        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        this.mAllDataDisconnectedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onSetInternalDataEnabled(boolean enable) {
        log("onSetInternalDataEnabled: enabled=" + enable);
        onSetInternalDataEnabled(enable, null);
    }

    protected void onSetInternalDataEnabled(boolean enabled, Message onCompleteMsg) {
        log("onSetInternalDataEnabled: enabled=" + enabled);
        boolean sendOnComplete = true;
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                sendOnComplete = false;
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections((String) null, onCompleteMsg);
            }
        }
        if (sendOnComplete && onCompleteMsg != null) {
            onCompleteMsg.sendToTarget();
        }
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        log("setInternalDataEnabledFlag(" + enable + ")");
        if (this.mInternalDataEnabled == enable) {
            return true;
        }
        this.mInternalDataEnabled = enable;
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean setInternalDataEnabled(boolean enable) {
        return setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        int i;
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363, onCompleteMsg);
        if (enable) {
            i = 1;
        } else {
            i = 0;
        }
        msg.arg1 = i;
        sendMessage(msg);
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void setDataAllowed(boolean enable, Message response) {
        log("setDataAllowed: enable=" + enable);
        mIsCleanupRequired = !enable;
        this.mPhone.mCi.setDataAllowed(enable, response);
        this.mInternalDataEnabled = enable;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void log(String s) {
        Rlog.d(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void loge(String s) {
        Rlog.e(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mReregisterOnReconnectFailure=" + this.mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + this.mCanSetPreferApn);
        pw.println(" mApnObserver=" + this.mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + this.mDataConnectionAcHashMap);
        pw.println(" mAttached=" + this.mAttached.get());
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public String[] getPcscfAddress(String apnType) {
        ApnContext apnContext;
        log("getPcscfAddress()");
        if (apnType == null) {
            log("apnType is null, return null");
            return null;
        }
        if (TextUtils.equals(apnType, "emergency")) {
            apnContext = (ApnContext) this.mApnContexts.get("emergency");
        } else if (TextUtils.equals(apnType, "ims")) {
            apnContext = (ApnContext) this.mApnContexts.get("ims");
        } else {
            log("apnType is invalid, return null");
            return null;
        }
        if (apnContext == null) {
            log("apnContext is null, return null");
            return null;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        if (dcac == null) {
            return null;
        }
        String[] result = dcac.getPcscfAddr();
        for (int i = 0; i < result.length; i++) {
            log("Pcscf[" + i + "]: " + result[i]);
        }
        return result;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void setImsRegistrationState(boolean registered) {
        ServiceStateTracker sst;
        log("setImsRegistrationState - mImsRegistrationState(before): " + this.mImsRegistrationState + ", registered(current) : " + registered);
        if (this.mPhone != null && (sst = this.mPhone.getServiceStateTracker()) != null) {
            sst.setImsRegistrationState(registered);
        }
    }

    private void initEmergencyApnSetting() {
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(Telephony.Carriers.CONTENT_URI, null, "type=\"emergency\"", null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                this.mEmergencyApn = makeApnSetting(cursor);
            }
            cursor.close();
        }
    }

    private void addEmergencyApnSetting() {
        if (this.mEmergencyApn == null) {
            return;
        }
        if (this.mAllApnSettings == null) {
            this.mAllApnSettings = new ArrayList();
            return;
        }
        boolean hasEmergencyApn = false;
        Iterator i$ = this.mAllApnSettings.iterator();
        while (true) {
            if (i$.hasNext()) {
                if (ArrayUtils.contains(((ApnSetting) i$.next()).types, "emergency")) {
                    hasEmergencyApn = true;
                    break;
                }
            } else {
                break;
            }
        }
        if (!hasEmergencyApn) {
            this.mAllApnSettings.add(this.mEmergencyApn);
        } else {
            log("addEmergencyApnSetting - E-APN setting is already present");
        }
    }

    private void modifyOemKddiProfiles(String operator) {
        int oemProfileId = 0;
        ApnSetting defaultApn = null;
        if (operator != null) {
            if (this.mEnableApnCarNavi) {
                oemProfileId = CallFailCause.CDMA_INTERCEPT;
                this.mPreferredApn = getApnParameter(false, true, CallFailCause.CDMA_INTERCEPT, -1, operator, "");
                this.mAllApnSettings = new ArrayList();
                this.mAllApnSettings.add(this.mPreferredApn);
            } else {
                int i = 0;
                while (i < this.mAllApnSettings.size()) {
                    ApnSetting orgApn = (ApnSetting) this.mAllApnSettings.get(i);
                    switch (orgApn.profileId) {
                        case 1000:
                        case CallFailCause.CDMA_DROP /* 1001 */:
                            ApnSetting apn = getApnParameter(false, true, orgApn.profileId, orgApn.id, operator, orgApn.carrier);
                            this.mAllApnSettings.set(i, apn);
                            if (this.mPreferredApn != null) {
                                if (this.mPreferredApn.id != orgApn.id) {
                                    break;
                                } else {
                                    this.mPreferredApn = apn;
                                    oemProfileId = orgApn.profileId;
                                    break;
                                }
                            } else if (oemProfileId == 1000) {
                                break;
                            } else {
                                defaultApn = apn;
                                oemProfileId = orgApn.profileId;
                                break;
                            }
                        default:
                            if (orgApn.apn.indexOf(".au-net.ne.jp") == -1) {
                                break;
                            } else {
                                this.mAllApnSettings.remove(i);
                                i--;
                                break;
                            }
                    }
                    i++;
                }
            }
            if (this.mPreferredApn == null) {
                if (defaultApn != null) {
                    this.mPreferredApn = defaultApn;
                    setPreferredApn(this.mPreferredApn.id);
                } else {
                    return;
                }
            }
            switch (oemProfileId) {
                case 1000:
                case CallFailCause.CDMA_DROP /* 1001 */:
                    this.mOemKddiDunApn = getApnParameter(false, false, oemProfileId, -1, operator, "");
                    return;
                default:
                    this.mOemKddiDunApn = null;
                    return;
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void updateDunProfileName(int profileId, String dataProtocol, String apnName) {
        int headerSize = "QOEMHOOK".length() + 8;
        String radioTechnology = Integer.toString(1);
        String profile = Integer.toString(profileId);
        if (apnName == null) {
            apnName = "";
        }
        String authType = Integer.toString(0);
        if (dataProtocol == null) {
            dataProtocol = "IP";
        }
        int requestSize = radioTechnology.length() + 1 + profile.length() + 1 + apnName.length() + 1 + "".length() + 1 + "".length() + 1 + authType.length() + 1 + dataProtocol.length() + 1;
        byte[] request = new byte[headerSize + requestSize];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put("QOEMHOOK".getBytes());
        reqBuffer.putInt(591832);
        reqBuffer.putInt(requestSize);
        reqBuffer.put(radioTechnology.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(profile.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(apnName.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put("".getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put("".getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(authType.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(dataProtocol.getBytes());
        reqBuffer.put((byte) 0);
        this.mPhone.mCi.invokeOemRilRequestRaw(request, null);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void resetDunProfiles() {
        if (TelBrand.IS_DCM) {
            updateDunProfileName(1, "IP", "dcmtrg.ne.jp");
            for (int i = 2; i <= 10; i++) {
                updateDunProfileName(i, "IP", "LKvkWgIeq.o8s");
            }
            return;
        }
        for (int i2 = 1; i2 <= 10; i2++) {
            updateDunProfileName(i2, "IP", "LKvkWgIeq.o8s");
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void startNetStatPoll() {
        if (this.mIsPhysicalLinkUp && !this.mIsPsRestricted && this.mPhone.getServiceStateTracker().getCurrentDataConnectionState() == 0) {
            super.startNetStatPoll();
        }
    }
}
