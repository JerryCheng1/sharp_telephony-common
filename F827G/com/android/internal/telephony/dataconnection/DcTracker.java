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
import android.content.res.Resources.NotFoundException;
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
import android.provider.Settings.Global;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
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
    private int RetryEnableApn = 0;
    private RegistrantList mAllDataDisconnectedRegistrants = new RegistrantList();
    private ApnChangeObserver mApnObserver;
    private AtomicBoolean mAttached = new AtomicBoolean(false);
    private boolean mCanSetPreferApn = false;
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private boolean mDeregistrationAlarmState = false;
    private ArrayList<Message> mDisconnectAllCompleteMsgList = new ArrayList();
    protected int mDisconnectPendingCount = 0;
    private PendingIntent mImsDeregistrationDelayIntent = null;
    public boolean mImsRegistrationState = false;
    private CdmaApnProfileTracker mOmhApt;
    protected boolean mOosIsDisconnect = SystemProperties.getBoolean(PhoneBase.PROPERTY_OOS_IS_DISCONNECT, true);
    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private ProgressDialog mProvisioningSpinner;
    private boolean mReregisterOnReconnectFailure = false;
    Handler mSimRecordsLoadedHandler = new Handler() {
        public void handleMessage(Message message) {
            if (!DcTracker.this.mPhone.mIsTheCurrentActivePhone || DcTracker.this.mIsDisposed) {
                DcTracker.this.loge("Sim handler handleMessage: Ignore msgs since phone is inactive");
                return;
            }
            switch (message.what) {
                case 100:
                    DcTracker.this.log("EVENT_3GPP_RECORDS_LOADED");
                    DcTracker.this.onSimRecordsLoaded();
                    return;
                default:
                    return;
            }
        }
    };
    private long mSubId;
    private ApnContext mWaitCleanUpApnContext = null;
    private boolean mWwanIwlanCoexistFlag = false;
    private ApnSetting oldPreferredApn = null;

    /* renamed from: com.android.internal.telephony.dataconnection.DcTracker$2 */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver() {
            super(DcTracker.this.mDataConnectionTracker);
        }

        public void onChange(boolean z) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270355));
        }
    }

    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String str, String str2) {
            this.mNetworkOperator = str2;
            this.mProvisionUrl = str;
        }

        private void enableMobileProvisioning() {
            Message obtainMessage = DcTracker.this.obtainMessage(270373);
            obtainMessage.setData(Bundle.forPair("provisioningUrl", this.mProvisionUrl));
            DcTracker.this.sendMessage(obtainMessage);
        }

        private void setEnableFailFastMobileData(int i) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270372, i, 0));
        }

        public void onReceive(Context context, Intent intent) {
            DcTracker.this.mProvisioningSpinner = new ProgressDialog(context);
            DcTracker.this.mProvisioningSpinner.setTitle(this.mNetworkOperator);
            DcTracker.this.mProvisioningSpinner.setMessage(context.getText(17040983));
            DcTracker.this.mProvisioningSpinner.setIndeterminate(true);
            DcTracker.this.mProvisioningSpinner.setCancelable(true);
            DcTracker.this.mProvisioningSpinner.getWindow().setType(2009);
            DcTracker.this.mProvisioningSpinner.show();
            DcTracker.this.sendMessageDelayed(DcTracker.this.obtainMessage(270378, DcTracker.this.mProvisioningSpinner), 120000);
            DcTracker.this.setRadio(true);
            setEnableFailFastMobileData(1);
            enableMobileProvisioning();
        }
    }

    public DcTracker(PhoneBase phoneBase) {
        super(phoneBase);
        if (phoneBase.getPhoneType() == 1) {
            this.LOG_TAG = "GsmDCT";
        } else if (phoneBase.getPhoneType() == 2) {
            this.LOG_TAG = "CdmaDCT";
        } else {
            this.LOG_TAG = "DCT";
            loge("unexpected phone type [" + phoneBase.getPhoneType() + "]");
        }
        log(this.LOG_TAG + ".constructor");
        if (phoneBase.getPhoneType() == 2) {
            boolean z = phoneBase.getContext().getResources().getBoolean(17957036);
            log(this.LOG_TAG + " fetchApnFromOmhCard: " + z);
            if (z) {
                this.mOmhApt = new CdmaApnProfileTracker((CDMAPhone) phoneBase);
                this.mOmhApt.registerForModemProfileReady(this, 270379, null);
            }
        }
        this.mDataConnectionTracker = this;
        registerForAllEvents();
        update();
        this.mApnObserver = new ApnChangeObserver();
        phoneBase.getContext().getContentResolver().registerContentObserver(Carriers.CONTENT_URI, true, this.mApnObserver);
        initApnContexts();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.android.internal.telephony.data-reconnect." + apnContext.getApnType());
            intentFilter.addAction("com.android.internal.telephony.data-restart-trysetup." + apnContext.getApnType());
            this.mPhone.getContext().registerReceiver(this.mIntentReceiver, intentFilter, null, this.mPhone);
        }
        initEmergencyApnSetting();
        addEmergencyApnSetting();
        this.mProvisionActionName = "com.android.internal.telephony.PROVISION" + phoneBase.getPhoneId();
    }

    private ApnContext addApnContext(String str, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(this.mPhone.getContext(), str, this.LOG_TAG, networkConfig, this);
        this.mApnContexts.put(str, apnContext);
        this.mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    private void addDummyApnSettings(String str) {
        log("createAllApnList: Creating dummy apn for cdma operator:" + str);
        String[] strArr = new String[]{"default", "mms", "supl", "hipri", "fota", "ims", "cbs"};
        String str2 = str;
        this.mAllApnSettings.add(new ApnSetting(0, str2, null, null, null, null, null, null, null, null, null, 3, strArr, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0, 0, false, 0, 0, 0, 0, "", ""));
        strArr = new String[]{"dun"};
        str2 = str;
        this.mAllApnSettings.add(new ApnSetting(3, str2, null, null, null, null, null, null, null, null, null, 3, strArr, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0, 0, false, 0, 0, 0, 0, "", ""));
    }

    private void addEmergencyApnSetting() {
        if (this.mEmergencyApn == null) {
            return;
        }
        if (this.mAllApnSettings == null) {
            this.mAllApnSettings = new ArrayList();
            return;
        }
        Object obj;
        Iterator it = this.mAllApnSettings.iterator();
        while (it.hasNext()) {
            if (ArrayUtils.contains(((ApnSetting) it.next()).types, "emergency")) {
                obj = 1;
                break;
            }
        }
        obj = null;
        if (obj == null) {
            this.mAllApnSettings.add(this.mEmergencyApn);
        } else {
            log("addEmergencyApnSetting - E-APN setting is already present");
        }
    }

    private String apnListToString(ArrayList<ApnSetting> arrayList) {
        StringBuilder stringBuilder = new StringBuilder();
        int size = arrayList.size();
        for (int i = 0; i < size; i++) {
            stringBuilder.append('[').append(((ApnSetting) arrayList.get(i)).toString()).append(']');
        }
        return stringBuilder.toString();
    }

    private boolean apnTypeSameAny(ApnSetting apnSetting, ApnSetting apnSetting2) {
        int i = 0;
        while (i < apnSetting.types.length) {
            int i2 = 0;
            while (i2 < apnSetting2.types.length) {
                if (apnSetting.types[i].equals(CharacterSets.MIMENAME_ANY_CHARSET) || apnSetting2.types[i2].equals(CharacterSets.MIMENAME_ANY_CHARSET) || apnSetting.types[i].equals(apnSetting2.types[i2])) {
                    return true;
                }
                i2++;
            }
            i++;
        }
        return false;
    }

    private boolean apnsSimilar(ApnSetting apnSetting, ApnSetting apnSetting2) {
        return !apnSetting.canHandleType("dun") && !apnSetting2.canHandleType("dun") && Objects.equals(apnSetting.apn, apnSetting2.apn) && !apnTypeSameAny(apnSetting, apnSetting2) && xorEquals(apnSetting.proxy, apnSetting2.proxy) && xorEquals(apnSetting.port, apnSetting2.port) && apnSetting.carrierEnabled == apnSetting2.carrierEnabled && apnSetting.bearer == apnSetting2.bearer && apnSetting.profileId == apnSetting2.profileId && Objects.equals(apnSetting.mvnoType, apnSetting2.mvnoType) && Objects.equals(apnSetting.mvnoMatchData, apnSetting2.mvnoMatchData) && xorEquals(apnSetting.mmsc, apnSetting2.mmsc) && xorEquals(apnSetting.mmsProxy, apnSetting2.mmsProxy) && xorEquals(apnSetting.mmsPort, apnSetting2.mmsPort);
    }

    private void applyNewState(ApnContext apnContext, boolean z, boolean z2) {
        boolean z3;
        boolean z4;
        log("applyNewState(" + apnContext.getApnType() + ", " + z + "(" + apnContext.isEnabled() + "), " + z2 + "(" + apnContext.getDependencyMet() + "))");
        if (apnContext.isReady()) {
            if (z && z2) {
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
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        z3 = true;
                        z4 = true;
                        break;
                    default:
                        z3 = false;
                        z4 = true;
                        break;
                }
            } else if (z2) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
                if ((apnContext.getApnType() == "dun" && teardownForDun()) || apnContext.getApnType() == "mms") {
                    z3 = false;
                    z4 = true;
                } else {
                    z3 = false;
                    z4 = false;
                }
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
                z3 = false;
                z4 = true;
            }
        } else if (z && z2) {
            if (apnContext.isEnabled()) {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
            } else {
                apnContext.setReason(Phone.REASON_DATA_ENABLED);
            }
            if (apnContext.getState() == State.FAILED) {
                apnContext.setState(State.IDLE);
            }
            z3 = true;
            z4 = false;
        } else {
            z3 = false;
            z4 = false;
        }
        apnContext.setEnabled(z);
        apnContext.setDependencyMet(z2);
        if (z4) {
            cleanUpConnection(true, apnContext);
        }
        if (z3) {
            trySetupData(apnContext);
        }
        if (TelBrand.IS_DCM && z) {
            startNetStatPoll();
        }
    }

    private ArrayList<ApnSetting> buildWaitingApns(String str, int i) {
        boolean z = true;
        log("buildWaitingApns: E requestedApnType=" + str);
        ArrayList<ApnSetting> arrayList = new ArrayList();
        if (str.equals("dun")) {
            ApnSetting fetchDunApn = fetchDunApn();
            if (fetchDunApn != null) {
                arrayList.add(fetchDunApn);
                log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + arrayList);
                return arrayList;
            }
        }
        String operatorNumeric = getOperatorNumeric();
        if (!TelBrand.IS_DCM && !TelBrand.IS_KDDI) {
            try {
                if (this.mPhone.getContext().getResources().getBoolean(17956983)) {
                    z = false;
                }
            } catch (NotFoundException e) {
                log("buildWaitingApns: usePreferred NotFoundException set to true");
            }
            log("buildWaitingApns: usePreferred=" + z + " canSetPreferApn=" + this.mCanSetPreferApn + " mPreferredApn=" + this.mPreferredApn + " operator=" + operatorNumeric + " radioTech=" + i);
            if (z && this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(str)) {
                log("buildWaitingApns: Preferred APN:" + operatorNumeric + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                if (this.mPreferredApn.numeric == null || !this.mPreferredApn.numeric.equals(operatorNumeric)) {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                } else if (this.mPreferredApn.bearer == 0 || this.mPreferredApn.bearer == i) {
                    arrayList.add(this.mPreferredApn);
                    log("buildWaitingApns: X added preferred apnList=" + arrayList);
                    return arrayList;
                } else {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                }
            }
        } else if (!TelBrand.IS_KDDI || (this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(str))) {
            if (this.mCanSetPreferApn && this.mPreferredApn != null) {
                log("buildWaitingApns: Preferred APN:" + operatorNumeric + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                if (this.mPreferredApn.numeric != null && this.mPreferredApn.numeric.equals(operatorNumeric) && ((this.mPreferredApn.bearer == 0 || this.mPreferredApn.bearer == i) && this.mPreferredApn.canHandleType(str))) {
                    arrayList.add(this.mPreferredApn);
                    log("buildWaitingApns: X added preferred apnList=" + arrayList);
                    return arrayList;
                }
            }
            log("buildWaitingApns: no preferred APN");
            return arrayList;
        }
        if (this.mAllApnSettings == null || this.mAllApnSettings.isEmpty()) {
            loge("mAllApnSettings is empty!");
        } else {
            log("buildWaitingApns: mAllApnSettings=" + this.mAllApnSettings);
            Iterator it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                ApnSetting apnSetting = (ApnSetting) it.next();
                log("buildWaitingApns: apn=" + apnSetting);
                if (!apnSetting.canHandleType(str)) {
                    log("buildWaitingApns: couldn't handle requesedApnType=" + str);
                } else if (apnSetting.bearer == 0 || apnSetting.bearer == i) {
                    log("buildWaitingApns: adding apn=" + apnSetting.toString());
                    arrayList.add(apnSetting);
                } else {
                    log("buildWaitingApns: bearer:" + apnSetting.bearer + " != " + "radioTech:" + i);
                }
            }
        }
        log("buildWaitingApns: X apnList=" + arrayList);
        return arrayList;
    }

    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext != null) {
            PendingIntent reconnectIntent = apnContext.getReconnectIntent();
            if (reconnectIntent != null) {
                ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(reconnectIntent);
                apnContext.setReconnectIntent(null);
            }
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting fetchDunApn = "dun".equals(apnType) ? fetchDunApn() : null;
        log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext);
        Object obj = null;
        Object obj2 = null;
        for (ApnContext apnContext2 : this.mApnContexts.values()) {
            DcAsyncChannel dcAc = apnContext2.getDcAc();
            log("curDcac: " + dcAc);
            if (dcAc != null) {
                ApnSetting apnSetting = apnContext2.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (fetchDunApn == null) {
                    if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext2.getState().ordinal()]) {
                            case 1:
                                log("checkForCompatibleConnectedApnContext: found canHandle conn=" + dcAc + " curApnCtx=" + apnContext2);
                                return dcAc;
                            case 2:
                                if (obj == null && apnSetting.equals(apnContext.getNextWaitingApn())) {
                                    obj = dcAc;
                                    obj2 = apnContext2;
                                    break;
                                }
                            case 3:
                            case 4:
                                obj = dcAc;
                                obj2 = apnContext2;
                                break;
                            default:
                                break;
                        }
                    }
                } else if (fetchDunApn.equals(apnSetting)) {
                    switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext2.getState().ordinal()]) {
                        case 1:
                            log("checkForCompatibleConnectedApnContext: found dun conn=" + dcAc + " curApnCtx=" + apnContext2);
                            return dcAc;
                        case 2:
                            if (obj != null) {
                                break;
                            }
                            obj = dcAc;
                            obj2 = apnContext2;
                            break;
                        case 3:
                        case 4:
                            obj = dcAc;
                            obj2 = apnContext2;
                            break;
                        default:
                            break;
                    }
                } else {
                    continue;
                }
            }
        }
        if (obj != null) {
            log("checkForCompatibleConnectedApnContext: found potential conn=" + obj + " curApnCtx=" + obj2);
            return obj;
        }
        log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    private ArrayList<ApnSetting> create3gppApnsList() {
        createAllApnList();
        return this.mAllApnSettings;
    }

    private void createAllApnList() {
        this.mAllApnSettings.clear();
        String operatorNumeric = getOperatorNumeric();
        int rilDataRadioTechnology = this.mPhone.getServiceState().getRilDataRadioTechnology();
        if (!(this.mOmhApt == null || !ServiceState.isCdma(rilDataRadioTechnology) || 13 == rilDataRadioTechnology)) {
            ArrayList arrayList = new ArrayList();
            arrayList = this.mOmhApt.getOmhApnProfilesList();
            if (!arrayList.isEmpty()) {
                log("createAllApnList: Copy Omh profiles");
                this.mAllApnSettings.addAll(arrayList);
            }
        }
        if (!(!this.mAllApnSettings.isEmpty() || operatorNumeric == null || operatorNumeric.isEmpty())) {
            String str = ("numeric = '" + operatorNumeric + "'") + " and carrier_enabled = 1";
            log("createAllApnList: selection=" + str);
            Cursor query = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, str, null, null);
            if (query != null) {
                if (query.getCount() > 0) {
                    this.mAllApnSettings = createApnList(query, (IccRecords) this.mIccRecords.get());
                }
                query.close();
            }
        }
        dedupeApnSettings();
        if (this.mAllApnSettings.isEmpty() && isDummyProfileNeeded()) {
            addDummyApnSettings(operatorNumeric);
        }
        addEmergencyApnSetting();
        if (this.mAllApnSettings.isEmpty()) {
            log("createAllApnList: No APN found for carrier: " + operatorNumeric);
            this.mPreferredApn = null;
        } else {
            this.mPreferredApn = getPreferredApn();
            if (!(this.mPreferredApn == null || this.mPreferredApn.numeric.equals(operatorNumeric))) {
                this.mPreferredApn = null;
                setPreferredApn(-1);
            }
            if (TelBrand.IS_DCM && this.mPreferredApn == null && this.oldPreferredApn == null && this.mAllApnSettings.size() > 0) {
                Iterator it = this.mAllApnSettings.iterator();
                while (it.hasNext()) {
                    ApnSetting apnSetting = (ApnSetting) it.next();
                    if ((APN_DCM_SPMODE.equals(apnSetting.apn) && apnSetting.canHandleType("default") && apnSetting.id == 1) || (APN_DCM_TEST.equals(apnSetting.apn) && apnSetting.canHandleType("default") && apnSetting.id == 2)) {
                        this.mPreferredApn = apnSetting;
                        setPreferredApn(this.mPreferredApn.id);
                        log("mPreferredApn == null --> mPreferredApn=" + this.mPreferredApn);
                        break;
                    }
                }
            }
            log("createAllApnList: mPreferredApn=" + this.mPreferredApn);
        }
        if (TelBrand.IS_KDDI) {
            modifyOemKddiProfiles(operatorNumeric);
        }
        log("createAllApnList: X mAllApnSettings=" + this.mAllApnSettings);
        setDataProfilesAsNeeded();
    }

    private ArrayList<ApnSetting> createApnList(Cursor cursor, IccRecords iccRecords) {
        Object arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        if (cursor.moveToFirst()) {
            do {
                ApnSetting makeApnSetting = makeApnSetting(cursor);
                if (makeApnSetting != null) {
                    if (!makeApnSetting.hasMvnoParams()) {
                        arrayList.add(makeApnSetting);
                    } else if (iccRecords != null && mvnoMatches(iccRecords, makeApnSetting.mvnoType, makeApnSetting.mvnoMatchData)) {
                        arrayList2.add(makeApnSetting);
                    }
                }
            } while (cursor.moveToNext());
        }
        if (!arrayList2.isEmpty()) {
            ArrayList arrayList3 = arrayList2;
        }
        log("createApnList: X result=" + arrayList3);
        return arrayList3;
    }

    private DcAsyncChannel createDataConnection() {
        log("createDataConnection E");
        int andIncrement = this.mUniqueIdGenerator.getAndIncrement();
        DataConnection makeDataConnection = DataConnection.makeDataConnection(this.mPhone, andIncrement, this, this.mDcTesterFailBringUpAll, this.mDcc);
        this.mDataConnections.put(Integer.valueOf(andIncrement), makeDataConnection);
        DcAsyncChannel dcAsyncChannel = new DcAsyncChannel(makeDataConnection, this.LOG_TAG);
        int fullyConnectSync = dcAsyncChannel.fullyConnectSync(this.mPhone.getContext(), this, makeDataConnection.getHandler());
        if (fullyConnectSync == 0) {
            this.mDataConnectionAcHashMap.put(Integer.valueOf(dcAsyncChannel.getDataConnectionIdSync()), dcAsyncChannel);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcAsyncChannel + " status=" + fullyConnectSync);
        }
        log("createDataConnection() X id=" + andIncrement + " dc=" + makeDataConnection);
        return dcAsyncChannel;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcAsyncChannel) {
        log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcAsyncChannel);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getDcAc() == dcAsyncChannel) {
                log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        log("dataConnectionNotInUse: tearDownAll");
        dcAsyncChannel.tearDownAll("No connection", null);
        log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private void dedupeApnSettings() {
        ArrayList arrayList = new ArrayList();
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mAllApnSettings.size() - 1) {
                int i3 = i2 + 1;
                ApnSetting apnSetting = (ApnSetting) this.mAllApnSettings.get(i2);
                while (i3 < this.mAllApnSettings.size()) {
                    ApnSetting apnSetting2 = (ApnSetting) this.mAllApnSettings.get(i3);
                    if (apnsSimilar(apnSetting, apnSetting2)) {
                        apnSetting2 = mergeApns(apnSetting, apnSetting2);
                        this.mAllApnSettings.set(i2, apnSetting2);
                        this.mAllApnSettings.remove(i3);
                        apnSetting = apnSetting2;
                    } else {
                        i3++;
                    }
                }
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private void destroyDataConnections() {
        if (this.mDataConnections != null) {
            log("destroyDataConnections: clear mDataConnectionList");
            this.mDataConnections.clear();
            return;
        }
        log("destroyDataConnections: mDataConnecitonList is empty, ignore");
    }

    private DcAsyncChannel findDataConnectionAcByCid(int i) {
        for (DcAsyncChannel dcAsyncChannel : this.mDataConnectionAcHashMap.values()) {
            if (dcAsyncChannel.getCidSync() == i) {
                return dcAsyncChannel;
            }
        }
        return null;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcAsyncChannel : this.mDataConnectionAcHashMap.values()) {
            if (dcAsyncChannel.isInactiveSync() && dataConnectionNotInUse(dcAsyncChannel)) {
                log("findFreeDataConnection: found free DataConnection= dcac=" + dcAsyncChannel);
                return dcAsyncChannel;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private int getApnDelay(String str) {
        return (this.mFailFast || Phone.REASON_NW_TYPE_CHANGED.equals(str) || Phone.REASON_APN_CHANGED.equals(str)) ? SystemProperties.getInt("persist.radio.apn_ff_delay", 3000) : SystemProperties.getInt("persist.radio.apn_delay", 20000);
    }

    private int getCellLocationId() {
        CellLocation cellLocation = this.mPhone.getCellLocation();
        if (cellLocation != null) {
            if (cellLocation instanceof GsmCellLocation) {
                return ((GsmCellLocation) cellLocation).getCid();
            }
            if (cellLocation instanceof CdmaCellLocation) {
                return ((CdmaCellLocation) cellLocation).getBaseStationId();
            }
        }
        return -1;
    }

    private String getOperatorNumeric() {
        String str;
        if (isNvSubscription()) {
            str = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            log("getOperatorNumberic - returning from NV: " + str);
        } else {
            IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
            str = iccRecords != null ? iccRecords.getOperatorNumeric() : "";
            log("getOperatorNumberic - returning from card: " + str);
        }
        return str == null ? "" : str;
    }

    private ApnSetting getPreferredApn() {
        if (this.mAllApnSettings.isEmpty()) {
            log("getPreferredApn: X not found mAllApnSettings.isEmpty");
            return null;
        }
        Cursor query = this.mPhone.getContext().getContentResolver().query(Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString((long) this.mPhone.getSubId())), new String[]{"_id", "name", Carriers.APN}, null, null, Carriers.DEFAULT_SORT_ORDER);
        if (query != null) {
            this.mCanSetPreferApn = true;
        } else {
            this.mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + this.mRequestedApnType + " cursor=" + query + " cursor.count=" + (query != null ? query.getCount() : 0));
        if (this.mCanSetPreferApn && query.getCount() > 0) {
            query.moveToFirst();
            int i = query.getInt(query.getColumnIndexOrThrow("_id"));
            Iterator it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                ApnSetting apnSetting = (ApnSetting) it.next();
                log("getPreferredApn: apnSetting=" + apnSetting);
                if (apnSetting.id == i && apnSetting.canHandleType(this.mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + apnSetting);
                    query.close();
                    return apnSetting;
                }
            }
        }
        if (query != null) {
            query.close();
        }
        log("getPreferredApn: X not found");
        return null;
    }

    private IccRecords getUiccRecords(int i) {
        return this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), i);
    }

    private boolean imsiMatches(String str, String str2) {
        int length = str.length();
        if (length <= 0 || length > str2.length()) {
            return false;
        }
        int i = 0;
        while (i < length) {
            char charAt = str.charAt(i);
            if (charAt != 'x' && charAt != 'X' && charAt != str2.charAt(i)) {
                return false;
            }
            i++;
        }
        return true;
    }

    private void initEmergencyApnSetting() {
        Cursor query = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, "type=\"emergency\"", null, null);
        if (query != null) {
            if (query.getCount() > 0 && query.moveToFirst()) {
                this.mEmergencyApn = makeApnSetting(query);
            }
            query.close();
        }
    }

    private boolean isDataAllowed(ApnContext apnContext) {
        if ((apnContext.getApnType().equals("default") || apnContext.getApnType().equals("ia")) && this.mPhone.getServiceState().getRilDataRadioTechnology() == 18 && !this.mWwanIwlanCoexistFlag) {
            log("Default data call activation not allowed in iwlan.");
        } else if (apnContext.isReady() && isDataAllowed()) {
            return true;
        }
        return false;
    }

    private boolean isDummyProfileNeeded() {
        int familyFromRadioTechnology = UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology());
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        log("isDummyProfileNeeded: radioTechFam = " + familyFromRadioTechnology);
        return familyFromRadioTechnology == 2 || (familyFromRadioTechnology == -1 && iccRecords != null && (iccRecords instanceof RuimRecords));
    }

    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        Iterator it = this.mPrioritySortedApnContexts.iterator();
        while (it.hasNext()) {
            ApnContext apnContext2 = (ApnContext) it.next();
            if (apnContext.getApnType().equalsIgnoreCase(apnContext2.getApnType())) {
                break;
            } else if (apnContext2.isEnabled() && apnContext2.getState() != State.FAILED) {
                return true;
            }
        }
        return false;
    }

    private boolean isNvSubscription() {
        return this.mCdmaSsm != null && UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology()) == 2 && this.mCdmaSsm.getCdmaSubscriptionSource() == 1;
    }

    private boolean isOnlySingleDcAllowed(int i) {
        int i2 = 0;
        int[] intArray = this.mPhone.getContext().getResources().getIntArray(17236015);
        boolean z = Build.IS_DEBUGGABLE && SystemProperties.getBoolean("persist.telephony.test.singleDc", false);
        if (intArray != null) {
            while (i2 < intArray.length && !z) {
                if (i == intArray[i2]) {
                    z = true;
                }
                i2++;
            }
        }
        log("isOnlySingleDcAllowed(" + i + "): " + z);
        return z;
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        ApnSetting apnSetting = new ApnSetting(cursor.getInt(cursor.getColumnIndexOrThrow("_id")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.NUMERIC)), cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PORT)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSC))), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPORT)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.USER)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PASSWORD)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.AUTH_TYPE)), parseTypes(cursor.getString(cursor.getColumnIndexOrThrow("type"))), cursor.getString(cursor.getColumnIndexOrThrow("protocol")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.ROAMING_PROTOCOL)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.CARRIER_ENABLED)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.BEARER)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.PROFILE_ID)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MODEM_COGNITIVE)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.WAIT_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow("mtu")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_TYPE)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_MATCH_DATA)));
        if (TelBrand.IS_KDDI) {
            String[] strArr = new String[2];
            try {
                strArr[0] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_primary"));
                strArr[1] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_secoundary"));
            } catch (Exception e) {
                strArr[0] = "";
                strArr[1] = "";
            }
            apnSetting.oemDnses = strArr;
        }
        return apnSetting;
    }

    private ApnSetting mergeApns(ApnSetting apnSetting, ApnSetting apnSetting2) {
        ArrayList arrayList = new ArrayList();
        arrayList.addAll(Arrays.asList(apnSetting.types));
        for (Object obj : apnSetting2.types) {
            if (!arrayList.contains(obj)) {
                arrayList.add(obj);
            }
        }
        String str = TextUtils.isEmpty(apnSetting.mmsc) ? apnSetting2.mmsc : apnSetting.mmsc;
        String str2 = TextUtils.isEmpty(apnSetting.mmsProxy) ? apnSetting2.mmsProxy : apnSetting.mmsProxy;
        String str3 = TextUtils.isEmpty(apnSetting.mmsPort) ? apnSetting2.mmsPort : apnSetting.mmsPort;
        String str4 = TextUtils.isEmpty(apnSetting.proxy) ? apnSetting2.proxy : apnSetting.proxy;
        String str5 = TextUtils.isEmpty(apnSetting.port) ? apnSetting2.port : apnSetting.port;
        String str6 = apnSetting2.protocol.equals("IPV4V6") ? apnSetting2.protocol : apnSetting.protocol;
        String str7 = apnSetting2.roamingProtocol.equals("IPV4V6") ? apnSetting2.roamingProtocol : apnSetting.roamingProtocol;
        int i = apnSetting.id;
        String str8 = apnSetting.numeric;
        String str9 = apnSetting.carrier;
        String str10 = apnSetting.apn;
        String str11 = apnSetting.user;
        String str12 = apnSetting.password;
        int i2 = apnSetting.authType;
        String[] strArr = (String[]) arrayList.toArray(new String[0]);
        boolean z = apnSetting.carrierEnabled;
        int i3 = apnSetting.bearer;
        int i4 = apnSetting.profileId;
        boolean z2 = apnSetting.modemCognitive || apnSetting2.modemCognitive;
        return new ApnSetting(i, str8, str9, str10, str4, str5, str, str2, str3, str11, str12, i2, strArr, str6, str7, z, i3, i4, z2, apnSetting.maxConns, apnSetting.waitTime, apnSetting.maxConnsTime, apnSetting.mtu, apnSetting.mvnoType, apnSetting.mvnoMatchData);
    }

    private void modifyOemKddiProfiles(java.lang.String r12) {
        /*
        r11 = this;
        r0 = 0;
        r2 = 0;
        r10 = 0;
        if (r12 != 0) goto L_0x0006;
    L_0x0005:
        return;
    L_0x0006:
        r1 = r11.mEnableApnCarNavi;
        if (r1 == 0) goto L_0x0040;
    L_0x000a:
        r8 = 1002; // 0x3ea float:1.404E-42 double:4.95E-321;
        r1 = 0;
        r2 = 1;
        r3 = 1002; // 0x3ea float:1.404E-42 double:4.95E-321;
        r4 = -1;
        r6 = "";
        r0 = r11;
        r5 = r12;
        r0 = r0.getApnParameter(r1, r2, r3, r4, r5, r6);
        r11.mPreferredApn = r0;
        r0 = new java.util.ArrayList;
        r0.<init>();
        r11.mAllApnSettings = r0;
        r0 = r11.mAllApnSettings;
        r1 = r11.mPreferredApn;
        r0.add(r1);
    L_0x0029:
        r3 = r8;
        r0 = r11.mPreferredApn;
        if (r0 != 0) goto L_0x0039;
    L_0x002e:
        if (r10 == 0) goto L_0x0005;
    L_0x0030:
        r11.mPreferredApn = r10;
        r0 = r11.mPreferredApn;
        r0 = r0.id;
        r11.setPreferredApn(r0);
    L_0x0039:
        switch(r3) {
            case 1000: goto L_0x00a2;
            case 1001: goto L_0x00a2;
            default: goto L_0x003c;
        };
    L_0x003c:
        r0 = 0;
        r11.mOemKddiDunApn = r0;
        goto L_0x0005;
    L_0x0040:
        r1 = 0;
        r8 = r0;
        r9 = r1;
        r10 = r2;
    L_0x0044:
        r0 = r11.mAllApnSettings;
        r0 = r0.size();
        if (r9 >= r0) goto L_0x0029;
    L_0x004c:
        r0 = r11.mAllApnSettings;
        r0 = r0.get(r9);
        r7 = r0;
        r7 = (com.android.internal.telephony.dataconnection.ApnSetting) r7;
        r0 = r7.profileId;
        switch(r0) {
            case 1000: goto L_0x0074;
            case 1001: goto L_0x0074;
            default: goto L_0x005a;
        };
    L_0x005a:
        r0 = r7.apn;
        r1 = ".au-net.ne.jp";
        r0 = r0.indexOf(r1);
        r1 = -1;
        if (r0 == r1) goto L_0x00b1;
    L_0x0065:
        r0 = r11.mAllApnSettings;
        r0.remove(r9);
        r9 = r9 + -1;
        r0 = r8;
        r2 = r10;
    L_0x006e:
        r1 = r9 + 1;
        r8 = r0;
        r9 = r1;
        r10 = r2;
        goto L_0x0044;
    L_0x0074:
        r1 = 0;
        r2 = 1;
        r3 = r7.profileId;
        r4 = r7.id;
        r6 = r7.carrier;
        r0 = r11;
        r5 = r12;
        r2 = r0.getApnParameter(r1, r2, r3, r4, r5, r6);
        r0 = r11.mAllApnSettings;
        r0.set(r9, r2);
        r0 = r11.mPreferredApn;
        if (r0 != 0) goto L_0x0093;
    L_0x008b:
        r0 = 1000; // 0x3e8 float:1.401E-42 double:4.94E-321;
        if (r8 == r0) goto L_0x00b1;
    L_0x008f:
        r8 = r7.profileId;
        r0 = r8;
        goto L_0x006e;
    L_0x0093:
        r0 = r11.mPreferredApn;
        r0 = r0.id;
        r1 = r7.id;
        if (r0 != r1) goto L_0x00b1;
    L_0x009b:
        r11.mPreferredApn = r2;
        r8 = r7.profileId;
        r0 = r8;
        r2 = r10;
        goto L_0x006e;
    L_0x00a2:
        r1 = 0;
        r2 = 0;
        r4 = -1;
        r6 = "";
        r0 = r11;
        r5 = r12;
        r0 = r0.getApnParameter(r1, r2, r3, r4, r5, r6);
        r11.mOemKddiDunApn = r0;
        goto L_0x0005;
    L_0x00b1:
        r0 = r8;
        r2 = r10;
        goto L_0x006e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTracker.modifyOemKddiProfiles(java.lang.String):void");
    }

    private void notifyNoData(DcFailCause dcFailCause, ApnContext apnContext) {
        log("notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFail(dcFailCause) && !apnContext.getApnType().equals("default")) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onApnChanged() {
        log("onApnChanged: tryRestartDataConnections");
        setInitialAttachApn(create3gppApnsList(), (IccRecords) this.mSimRecords.get());
        tryRestartDataConnections(true, Phone.REASON_APN_CHANGED);
    }

    private void onDataConnectionAttached() {
        log("onDataConnectionAttached");
        this.mAttached.set(true);
        if (getOverallState() == State.CONNECTED) {
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

    private void onModemApnProfileReady() {
        if (this.mState == State.FAILED) {
            cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
        }
        log("OMH: onModemApnProfileReady(): Setting up data call");
        tryRestartDataConnections(false, Phone.REASON_SIM_LOADED);
    }

    private void onNvReady() {
        log("onNvReady");
        createAllApnList();
        setupDataOnConnectableApns(Phone.REASON_NV_READY);
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

    private void onSimRecordsLoaded() {
        setInitialAttachApn(create3gppApnsList(), (IccRecords) this.mSimRecords.get());
    }

    private void onWwanIwlanCoexistenceDone(AsyncResult asyncResult) {
        if (asyncResult.exception != null) {
            log("onWwanIwlanCoexistenceDone: error = " + asyncResult.exception);
            return;
        }
        byte[] bArr = (byte[]) asyncResult.result;
        log("onWwanIwlanCoexistenceDone, payload hexdump = " + IccUtils.bytesToHexString(bArr));
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        byte b = wrap.get();
        log("onWwanIwlanCoexistenceDone: resp = " + b);
        boolean z = b > (byte) 0;
        if (this.mWwanIwlanCoexistFlag == z) {
            log("onWwanIwlanCoexistenceDone: no change in status, ignore.");
            return;
        }
        this.mWwanIwlanCoexistFlag = z;
        if (this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            log("notifyDataConnection IWLAN_AVAILABLE");
            notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
        }
    }

    private String[] parseTypes(String str) {
        if (str != null && !str.equals("")) {
            return str.split(",");
        }
        return new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
    }

    private boolean retryAfterDisconnected(ApnContext apnContext) {
        return (Phone.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason()) || (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isHigherPriorityApnContextActive(apnContext))) ? false : true;
    }

    private void setPreferredApn(int i) {
        if (this.mCanSetPreferApn) {
            Uri withAppendedPath = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString((long) this.mPhone.getSubId()));
            log("setPreferredApn: delete");
            ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
            contentResolver.delete(withAppendedPath, null, null);
            if (i >= 0) {
                log("setPreferredApn: insert");
                ContentValues contentValues = new ContentValues();
                contentValues.put(APN_ID, Integer.valueOf(i));
                contentResolver.insert(withAppendedPath, contentValues);
                return;
            }
            return;
        }
        log("setPreferredApn: X !canSEtPreferApn");
    }

    private void setRadio(boolean z) {
        try {
            Stub.asInterface(ServiceManager.checkService("phone")).setRadio(z);
        } catch (Exception e) {
        }
    }

    private boolean setupData(ApnContext apnContext, int i) {
        log("setupData: apnContext=" + apnContext);
        Object obj = null;
        Object nextWaitingApn = apnContext.getNextWaitingApn();
        if (nextWaitingApn == null) {
            log("setupData: return for no apn found!");
            return false;
        }
        int i2 = nextWaitingApn.profileId;
        if (i2 == 0) {
            i2 = getApnProfileID(apnContext.getApnType());
        }
        if (!(apnContext.getApnType() == "dun" && teardownForDun())) {
            obj = checkForCompatibleConnectedApnContext(apnContext);
            if (obj != null) {
                ApnSetting apnSettingSync = obj.getApnSettingSync();
                if (apnSettingSync != null) {
                    nextWaitingApn = apnSettingSync;
                }
            }
        }
        if (obj == null) {
            if (isOnlySingleDcAllowed(i)) {
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
            obj = findFreeDataConnection();
            if (obj == null) {
                obj = createDataConnection();
            }
            if (obj == null) {
                log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        log("setupData: dcac=" + obj + " apnSetting=" + nextWaitingApn);
        apnContext.setDataConnectionAc(obj);
        apnContext.setApnSetting(nextWaitingApn);
        apnContext.setState(State.CONNECTING);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        Message obtainMessage = obtainMessage();
        obtainMessage.what = 270336;
        obtainMessage.obj = apnContext;
        obj.bringUp(apnContext, getInitialMaxRetry(), i2, i, this.mAutoAttachOnCreation, obtainMessage);
        log("setupData: initing!");
        return true;
    }

    private void setupDataOnConnectableApns(String str) {
        log("setupDataOnConnectableApns: " + str);
        Iterator it = this.mPrioritySortedApnContexts.iterator();
        while (it.hasNext()) {
            ApnContext apnContext = (ApnContext) it.next();
            log("setupDataOnConnectableApns: apnContext " + apnContext);
            if (apnContext.getState() == State.FAILED) {
                if (TelBrand.IS_DCM) {
                    log("apnContext.getWaitingApnsPermFailCount(): " + apnContext.getWaitingApnsPermFailCount());
                    if (Phone.REASON_VOICE_CALL_ENDED.equals(str) && apnContext.getWaitingApnsPermFailCount() == 0) {
                    }
                }
                apnContext.setState(State.IDLE);
            }
            if (apnContext.isConnectable()) {
                log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                apnContext.setReason(str);
                trySetupData(apnContext);
            }
        }
    }

    private void startAlarmForReconnect(int i, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect." + apnType);
        intent.putExtra("reconnect_alarm_extra_reason", apnContext.getReason());
        intent.putExtra("reconnect_alarm_extra_type", apnType);
        int subId = this.mPhone.getSubId();
        intent.putExtra("subscription", subId);
        log("startAlarmForReconnect: delay=" + i + " action=" + intent.getAction() + " apn=" + apnContext + " subId=" + subId);
        PendingIntent broadcast = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(broadcast);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) i), broadcast);
    }

    private void startAlarmForRestartTrySetup(int i, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-restart-trysetup." + apnType);
        intent.putExtra("restart_trysetup_alarm_extra_type", apnType);
        log("startAlarmForRestartTrySetup: delay=" + i + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent broadcast = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(broadcast);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) i), broadcast);
    }

    private boolean teardownForDun() {
        return ServiceState.isCdma(this.mPhone.getServiceState().getRilDataRadioTechnology()) || fetchDunApn() != null;
    }

    private void tryRestartDataConnections(boolean z, String str) {
        boolean z2 = true;
        State overallState = getOverallState();
        boolean z3 = overallState == State.IDLE || overallState == State.FAILED;
        if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
        }
        log("tryRestartDataConnections: createAllApnList and cleanUpAllConnections");
        if (TelBrand.IS_DCM && this.mPreferredApn != null) {
            this.oldPreferredApn = this.mPreferredApn;
        } else if (TelBrand.IS_DCM && this.mPreferredApn == null && this.oldPreferredApn != null && str.equals(Phone.REASON_SIM_LOADED)) {
            this.mPreferredApn = this.oldPreferredApn;
            setPreferredApn(this.mPreferredApn.id);
        }
        createAllApnList();
        if (TelBrand.IS_KDDI && str.equals(Phone.REASON_APN_CHANGED) && this.mEnableApnCarNavi && !this.mConnectingApnCarNavi) {
            log("DcTracker mConnectingApnCarNavi = true");
            this.mConnectingApnCarNavi = true;
        }
        setInitialAttachApn();
        if (z) {
            if (z3) {
                z2 = false;
            }
            cleanUpAllConnections(z2, str);
        }
        if (z3 && this.mPhone.getSubId() == SubscriptionManager.getDefaultDataSubId()) {
            setupDataOnConnectableApns(str);
        } else if (TelBrand.IS_KDDI && this.mAttached.get()) {
            for (ApnContext apnContext : this.mApnContexts.values()) {
                if (apnContext.isReady() && apnContext.getState() == State.IDLE) {
                    startAlarmForReconnect(getApnDelay(str), apnContext);
                }
            }
        }
    }

    private boolean trySetupData(ApnContext apnContext) {
        boolean z = true;
        log("trySetupData for type:" + apnContext.getApnType() + " due to " + apnContext.getReason() + " apnContext=" + apnContext);
        log("trySetupData with mIsPsRestricted=" + this.mIsPsRestricted);
        if (TelBrand.IS_DCM) {
            String str = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + str);
            if (str.equals("trigger_restart_min_framework")) {
                log("Stop trySetupData while phone in de-encrypt view !!!");
                return false;
            }
        }
        if (this.mPhone.getSimulatedRadioControl() != null) {
            apnContext.setState(State.CONNECTED);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }
        boolean equals = apnContext.getApnType().equals("emergency");
        this.mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean z2 = !apnContext.getApnType().equals("ims");
        if (!apnContext.getApnType().equals("mms")) {
            z = z2;
        } else if (!z2 || this.mPhone.getContext().getResources().getBoolean(17957024)) {
            z = false;
        }
        if (apnContext.isConnectable() && (equals || (isDataAllowed(apnContext) && getAnyDataEnabled(z) && !isEmergency()))) {
            if (apnContext.getState() == State.FAILED) {
                log("trySetupData: make a FAILED ApnContext IDLE so its reusable");
                apnContext.setState(State.IDLE);
            }
            int rilDataRadioTechnology = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if (apnContext.getState() == State.IDLE) {
                ArrayList buildWaitingApns = buildWaitingApns(apnContext.getApnType(), rilDataRadioTechnology);
                if (buildWaitingApns.isEmpty()) {
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    log("trySetupData: X No APN found retValue=false");
                    return false;
                }
                apnContext.setWaitingApns(buildWaitingApns);
                log("trySetupData: Create from mAllApnSettings : " + apnListToString(this.mAllApnSettings));
            }
            log("trySetupData: call setupData, waitingApns : " + apnListToString(apnContext.getWaitingApns()));
            boolean z3 = setupData(apnContext, rilDataRadioTechnology);
            notifyOffApnsOfAvailability(apnContext.getReason());
            log("trySetupData: X retValue=" + z3);
            return z3;
        }
        if (TelBrand.IS_KDDI && (apnContext.getState() == State.IDLE || apnContext.getState() == State.SCANNING)) {
            if (this.mEnableApnCarNavi && this.mPhone.getServiceState().getRadioTechnology() == 0) {
                sendOemKddiFailCauseBroadcast(DcFailCause.SIGNAL_LOST, apnContext);
                changeMode(false, "", "", "", 0, "", "", "", "");
                this.mEnableApnCarNavi = false;
            } else if (this.mState != State.DISCONNECTING) {
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

    private void updateSimRecords() {
        if (this.mUiccController != null) {
            IccRecords uiccRecords = getUiccRecords(1);
            log("updateSimRecords: newSimRecords = " + uiccRecords);
            IccRecords iccRecords = (IccRecords) this.mSimRecords.get();
            if (iccRecords != uiccRecords) {
                if (iccRecords != null) {
                    log("Removing stale sim objects.");
                    iccRecords.unregisterForRecordsLoaded(this.mSimRecordsLoadedHandler);
                    this.mSimRecords.set(null);
                }
                if (uiccRecords != null) {
                    log("New sim records found");
                    this.mSimRecords.set(uiccRecords);
                    uiccRecords.registerForRecordsLoaded(this.mSimRecordsLoadedHandler, 100, null);
                }
            }
        }
    }

    private boolean xorEquals(String str, String str2) {
        return Objects.equals(str, str2) || TextUtils.isEmpty(str) || TextUtils.isEmpty(str2);
    }

    public void cleanUpAllConnections(String str) {
        cleanUpAllConnections(str, null);
    }

    public void cleanUpAllConnections(String str, Message message) {
        log("cleanUpAllConnections");
        if (message != null) {
            this.mDisconnectAllCompleteMsgList.add(message);
        }
        Message obtainMessage = obtainMessage(270365);
        obtainMessage.obj = str;
        sendMessage(obtainMessage);
    }

    /* Access modifiers changed, original: protected */
    public boolean cleanUpAllConnections(boolean z, String str) {
        boolean z2 = false;
        log("cleanUpAllConnections: tearDown=" + z + " reason=" + str);
        boolean equals = !TextUtils.isEmpty(str) ? str.equals(Phone.REASON_DATA_SPECIFIC_DISABLED) : false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                z2 = true;
            }
            if (!equals) {
                apnContext.setReason(str);
                cleanUpConnection(z, apnContext);
            } else if (!apnContext.getApnType().equals("ims")) {
                log("ApnConextType: " + apnContext.getApnType());
                apnContext.setReason(str);
                cleanUpConnection(z, apnContext);
            }
        }
        stopDataStallAlarm();
        this.mRequestedApnType = "default";
        log("cleanUpConnection: mDisconnectPendingCount = " + this.mDisconnectPendingCount);
        if (z && this.mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
        return z2;
    }

    /* Access modifiers changed, original: protected */
    public void cleanUpConnection(boolean z, ApnContext apnContext) {
        if (apnContext == null) {
            log("cleanUpConnection: apn context is null");
            return;
        }
        DcAsyncChannel dcAc = apnContext.getDcAc();
        log("cleanUpConnection: E tearDown=" + z + " reason=" + apnContext.getReason() + " apnContext=" + apnContext);
        if (!z) {
            if (dcAc != null) {
                dcAc.reqReset();
            }
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        } else if (apnContext.isDisconnected()) {
            apnContext.setState(State.IDLE);
            if (!apnContext.isReady()) {
                if (dcAc != null) {
                    dcAc.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
            }
        } else if (dcAc == null) {
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        } else if (apnContext.getState() != State.DISCONNECTING) {
            Object obj = null;
            if ("dun".equals(apnContext.getApnType()) && teardownForDun()) {
                log("tearing down dedicated DUN connection");
                obj = 1;
            }
            log("cleanUpConnection: tearing down" + (obj != null ? " all" : ""));
            Message obtainMessage = obtainMessage(270351, apnContext);
            if (obj != null) {
                apnContext.getDcAc().tearDownAll(apnContext.getReason(), obtainMessage);
            } else {
                apnContext.getDcAc().tearDown(apnContext, apnContext.getReason(), obtainMessage);
            }
            apnContext.setState(State.DISCONNECTING);
            this.mDisconnectPendingCount++;
        }
        if (this.mOmhApt != null) {
            this.mOmhApt.clearActiveApnProfile();
        }
        if (dcAc != null) {
            cancelReconnectAlarm(apnContext);
        }
        setupDataForSinglePdnArbitration(apnContext.getReason());
        log("cleanUpConnection: X tearDown=" + z + " reason=" + apnContext.getReason() + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
    }

    /* Access modifiers changed, original: protected */
    public void completeConnection(ApnContext apnContext) {
        apnContext.isProvisioningApn();
        log("completeConnection: successful, notify the world apnContext=" + apnContext);
        if (this.mIsProvisioning && !TextUtils.isEmpty(this.mProvisioningUrl)) {
            log("completeConnection: MOBILE_PROVISIONING_ACTION url=" + this.mProvisioningUrl);
            Intent makeMainSelectorActivity = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
            makeMainSelectorActivity.setData(Uri.parse(this.mProvisioningUrl));
            makeMainSelectorActivity.setFlags(272629760);
            try {
                this.mPhone.getContext().startActivity(makeMainSelectorActivity);
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

    public void decApnRefCount(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext != null) {
            apnContext.decRefCount();
        }
    }

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
        cleanUpAllConnections(true, null);
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("DcTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mReregisterOnReconnectFailure=" + this.mReregisterOnReconnectFailure);
        printWriter.println(" canSetPreferApn=" + this.mCanSetPreferApn);
        printWriter.println(" mApnObserver=" + this.mApnObserver);
        printWriter.println(" getOverallState=" + getOverallState());
        printWriter.println(" mDataConnectionAsyncChannels=%s\n" + this.mDataConnectionAcHashMap);
        printWriter.println(" mAttached=" + this.mAttached.get());
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("finalize");
    }

    public String getActiveApnString(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    public String[] getActiveApnTypes() {
        log("get all active apn types");
        ArrayList arrayList = new ArrayList();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                arrayList.add(apnContext.getApnType());
            }
        }
        return (String[]) arrayList.toArray(new String[0]);
    }

    /* JADX WARNING: Missing block: B:27:?, code skipped:
            return false;
     */
    public boolean getAnyDataEnabled() {
        /*
        r4 = this;
        r1 = 0;
        r2 = r4.mDataEnabledLock;
        monitor-enter(r2);
        r0 = r4.mInternalDataEnabled;	 Catch:{ all -> 0x0036 }
        if (r0 == 0) goto L_0x0014;
    L_0x0008:
        r0 = r4.mUserDataEnabled;	 Catch:{ all -> 0x0036 }
        if (r0 == 0) goto L_0x0014;
    L_0x000c:
        r0 = sPolicyDataEnabled;	 Catch:{ all -> 0x0036 }
        if (r0 == 0) goto L_0x0014;
    L_0x0010:
        r0 = r4.mUserDataEnabledDun;	 Catch:{ all -> 0x0036 }
        if (r0 != 0) goto L_0x0017;
    L_0x0014:
        monitor-exit(r2);	 Catch:{ all -> 0x0036 }
        r0 = r1;
    L_0x0016:
        return r0;
    L_0x0017:
        r0 = r4.mApnContexts;	 Catch:{ all -> 0x0036 }
        r0 = r0.values();	 Catch:{ all -> 0x0036 }
        r3 = r0.iterator();	 Catch:{ all -> 0x0036 }
    L_0x0021:
        r0 = r3.hasNext();	 Catch:{ all -> 0x0036 }
        if (r0 == 0) goto L_0x0039;
    L_0x0027:
        r0 = r3.next();	 Catch:{ all -> 0x0036 }
        r0 = (com.android.internal.telephony.dataconnection.ApnContext) r0;	 Catch:{ all -> 0x0036 }
        r0 = r4.isDataAllowed(r0);	 Catch:{ all -> 0x0036 }
        if (r0 == 0) goto L_0x0021;
    L_0x0033:
        monitor-exit(r2);	 Catch:{ all -> 0x0036 }
        r0 = 1;
        goto L_0x0016;
    L_0x0036:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0036 }
        throw r0;
    L_0x0039:
        monitor-exit(r2);	 Catch:{ all -> 0x0036 }
        r0 = r1;
        goto L_0x0016;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTracker.getAnyDataEnabled():boolean");
    }

    /* JADX WARNING: Missing block: B:29:?, code skipped:
            return false;
     */
    public boolean getAnyDataEnabled(boolean r5) {
        /*
        r4 = this;
        r1 = 0;
        r2 = r4.mDataEnabledLock;
        monitor-enter(r2);
        r0 = r4.mInternalDataEnabled;	 Catch:{ all -> 0x003a }
        if (r0 == 0) goto L_0x0018;
    L_0x0008:
        if (r5 == 0) goto L_0x000e;
    L_0x000a:
        r0 = r4.mUserDataEnabled;	 Catch:{ all -> 0x003a }
        if (r0 == 0) goto L_0x0018;
    L_0x000e:
        if (r5 == 0) goto L_0x0014;
    L_0x0010:
        r0 = sPolicyDataEnabled;	 Catch:{ all -> 0x003a }
        if (r0 == 0) goto L_0x0018;
    L_0x0014:
        r0 = r4.mUserDataEnabledDun;	 Catch:{ all -> 0x003a }
        if (r0 != 0) goto L_0x001b;
    L_0x0018:
        monitor-exit(r2);	 Catch:{ all -> 0x003a }
        r0 = r1;
    L_0x001a:
        return r0;
    L_0x001b:
        r0 = r4.mApnContexts;	 Catch:{ all -> 0x003a }
        r0 = r0.values();	 Catch:{ all -> 0x003a }
        r3 = r0.iterator();	 Catch:{ all -> 0x003a }
    L_0x0025:
        r0 = r3.hasNext();	 Catch:{ all -> 0x003a }
        if (r0 == 0) goto L_0x003d;
    L_0x002b:
        r0 = r3.next();	 Catch:{ all -> 0x003a }
        r0 = (com.android.internal.telephony.dataconnection.ApnContext) r0;	 Catch:{ all -> 0x003a }
        r0 = r4.isDataAllowed(r0);	 Catch:{ all -> 0x003a }
        if (r0 == 0) goto L_0x0025;
    L_0x0037:
        monitor-exit(r2);	 Catch:{ all -> 0x003a }
        r0 = 1;
        goto L_0x001a;
    L_0x003a:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x003a }
        throw r0;
    L_0x003d:
        monitor-exit(r2);	 Catch:{ all -> 0x003a }
        r0 = r1;
        goto L_0x001a;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTracker.getAnyDataEnabled(boolean):boolean");
    }

    public int getApnPriority(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + str);
        }
        return apnContext.priority;
    }

    /* Access modifiers changed, original: protected */
    public int getApnProfileID(String str) {
        return TextUtils.equals(str, "ims") ? 2 : TextUtils.equals(str, "fota") ? 3 : TextUtils.equals(str, "cbs") ? 4 : (TextUtils.equals(str, "ia") || !TextUtils.equals(str, "dun")) ? 0 : 1;
    }

    public LinkProperties getLinkProperties(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext != null) {
            DcAsyncChannel dcAc = apnContext.getDcAc();
            if (dcAc != null) {
                log("return link properites for " + str);
                return dcAc.getLinkPropertiesSync();
            }
        }
        log("return new LinkProperties");
        return new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext != null) {
            DcAsyncChannel dcAc = apnContext.getDcAc();
            if (dcAc != null) {
                log("get active pdp is not null, return NetworkCapabilities for " + str);
                return dcAc.getNetworkCapabilitiesSync();
            }
        }
        log("return new NetworkCapabilities");
        return new NetworkCapabilities();
    }

    public State getOverallState() {
        Object obj = null;
        Object obj2 = null;
        Object obj3 = 1;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext.getState().ordinal()]) {
                    case 1:
                    case 2:
                        log("overall state is CONNECTED");
                        return State.CONNECTED;
                    case 3:
                    case 4:
                        obj = 1;
                        obj2 = 1;
                        obj3 = null;
                        break;
                    case 5:
                    case 6:
                        obj2 = 1;
                        obj3 = null;
                        break;
                    default:
                        obj2 = 1;
                        break;
                }
            }
        }
        if (obj2 == null) {
            log("overall state is IDLE");
            return State.IDLE;
        } else if (obj != null) {
            log("overall state is CONNECTING");
            return State.CONNECTING;
        } else if (obj3 == null) {
            log("overall state is IDLE");
            return State.IDLE;
        } else {
            log("overall state is FAILED");
            return State.FAILED;
        }
    }

    public String[] getPcscfAddress(String str) {
        String[] strArr;
        log("getPcscfAddress()");
        if (str == null) {
            log("apnType is null, return null");
            strArr = null;
        } else {
            ApnContext apnContext;
            if (TextUtils.equals(str, "emergency")) {
                apnContext = (ApnContext) this.mApnContexts.get("emergency");
            } else if (TextUtils.equals(str, "ims")) {
                apnContext = (ApnContext) this.mApnContexts.get("ims");
            } else {
                log("apnType is invalid, return null");
                return null;
            }
            if (apnContext == null) {
                log("apnContext is null, return null");
                return null;
            }
            DcAsyncChannel dcAc = apnContext.getDcAc();
            if (dcAc == null) {
                return null;
            }
            strArr = dcAc.getPcscfAddr();
            for (int i = 0; i < strArr.length; i++) {
                log("Pcscf[" + i + "]: " + strArr[i]);
            }
        }
        return strArr;
    }

    public State getState(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        return apnContext != null ? apnContext.getState() : State.FAILED;
    }

    /* Access modifiers changed, original: protected */
    public void gotoIdleAndNotifyDataConnection(String str) {
        log("gotoIdleAndNotifyDataConnection: reason=" + str);
        notifyDataConnection(str);
        this.mActiveApn = null;
    }

    public void handleMessage(Message message) {
        boolean z = false;
        log("handleMessage msg=" + message);
        if (!this.mPhone.mIsTheCurrentActivePhone || this.mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
            return;
        }
        switch (message.what) {
            case 270338:
                onRecordsLoaded();
                return;
            case 270339:
                if (message.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext) message.obj);
                    return;
                } else if (message.obj instanceof String) {
                    onTrySetupData((String) message.obj);
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
                if (this.mState == State.FAILED) {
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
                if (message.arg1 != 0) {
                    z = true;
                }
                log("EVENT_CLEAN_UP_CONNECTION tearDown=" + z);
                if (message.obj instanceof ApnContext) {
                    cleanUpConnection(z, (ApnContext) message.obj);
                    return;
                }
                loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context, call super");
                super.handleMessage(message);
                return;
            case 270363:
                if (message.arg1 == 1) {
                    z = true;
                }
                onSetInternalDataEnabled(z, (Message) message.obj);
                return;
            case 270365:
                Message obtainMessage = obtainMessage(270365, null);
                if (message.obj != null && (message.obj instanceof String)) {
                    obtainMessage.obj = message.obj;
                }
                super.handleMessage(obtainMessage);
                return;
            case 270378:
                if (this.mProvisioningSpinner == message.obj) {
                    this.mProvisioningSpinner.dismiss();
                    this.mProvisioningSpinner = null;
                    return;
                }
                return;
            case 270379:
                onModemApnProfileReady();
                return;
            case 270380:
                onWwanIwlanCoexistenceDone((AsyncResult) message.obj);
                return;
            default:
                super.handleMessage(message);
                return;
        }
    }

    public void incApnRefCount(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext != null) {
            apnContext.incRefCount();
        }
    }

    /* Access modifiers changed, original: protected */
    public void initApnContexts() {
        log("initApnContexts: E");
        for (String networkConfig : this.mPhone.getContext().getResources().getStringArray(17235981)) {
            Object addApnContext;
            NetworkConfig networkConfig2 = new NetworkConfig(networkConfig);
            switch (networkConfig2.type) {
                case 0:
                    addApnContext = addApnContext("default", networkConfig2);
                    break;
                case 2:
                    addApnContext = addApnContext("mms", networkConfig2);
                    break;
                case 3:
                    addApnContext = addApnContext("supl", networkConfig2);
                    break;
                case 4:
                    addApnContext = addApnContext("dun", networkConfig2);
                    break;
                case 5:
                    addApnContext = addApnContext("hipri", networkConfig2);
                    break;
                case 10:
                    addApnContext = addApnContext("fota", networkConfig2);
                    break;
                case 11:
                    addApnContext = addApnContext("ims", networkConfig2);
                    break;
                case 12:
                    addApnContext = addApnContext("cbs", networkConfig2);
                    break;
                case 14:
                    addApnContext = addApnContext("ia", networkConfig2);
                    break;
                case 15:
                    addApnContext = addApnContext("emergency", networkConfig2);
                    break;
                default:
                    log("initApnContexts: skipping unknown type=" + networkConfig2.type);
                    continue;
            }
            log("initApnContexts: apnContext=" + addApnContext);
        }
        log("initApnContexts: X mApnContexts=" + this.mApnContexts);
    }

    public boolean isApnSupported(String str) {
        if (str == null) {
            loge("isApnSupported: name=null");
            return false;
        } else if (((ApnContext) this.mApnContexts.get(str)) != null) {
            return true;
        } else {
            loge("Request for unsupported mobile name: " + str);
            return false;
        }
    }

    public boolean isApnTypeActive(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        return (apnContext == null || apnContext.getDcAc() == null) ? false : true;
    }

    /* Access modifiers changed, original: protected */
    public boolean isApnTypeAvailable(String str) {
        if (str.equals("dun") && fetchDunApn() != null) {
            return true;
        }
        if (this.mAllApnSettings != null) {
            Iterator it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                if (((ApnSetting) it.next()).canHandleType(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isApnTypeEnabled(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        return apnContext == null ? false : apnContext.isEnabled();
    }

    /* Access modifiers changed, original: protected */
    public boolean isConnected() {
        for (ApnContext state : this.mApnContexts.values()) {
            if (state.getState() == State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    /* Access modifiers changed, original: protected */
    public boolean isDataAllowed() {
        boolean z;
        boolean z2;
        boolean z3 = false;
        synchronized (this.mDataEnabledLock) {
            z = this.mInternalDataEnabled;
        }
        boolean z4 = this.mAttached.get();
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        if (this.mPhone.getServiceState().getRilDataRadioTechnology() != 18 || desiredPowerState) {
            z2 = z4;
        } else {
            z2 = true;
            desiredPowerState = true;
        }
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            z4 = iccRecords.getRecordsLoaded();
            log("isDataAllowed getRecordsLoaded=" + z4);
        } else {
            z4 = false;
        }
        boolean isNvSubscription = isNvSubscription();
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
        boolean isValidSubscriptionId = SubscriptionManager.isValidSubscriptionId(defaultDataSubId);
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        if (this.mPhone.getCallTracker() != null) {
            state = this.mPhone.getCallTracker().getState();
        }
        if ((z2 || (this.mAutoAttachOnCreation && this.mPhone.getSubId() == defaultDataSubId)) && ((isNvSubscription || z4) && ((state == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) && z && isValidSubscriptionId && ((!this.mPhone.getServiceState().getDataRoaming() || getDataOnRoamingEnabled()) && !this.mIsPsRestricted && desiredPowerState)))) {
            z3 = true;
        }
        if (!z3) {
            String str = (z2 || this.mAutoAttachOnCreation) ? "" : "" + " - Attached= " + z2;
            String str2 = (isNvSubscription || z4) ? str : str + " - SIM not loaded and not NV subscription";
            if (!(state == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                str2 = (str2 + " - PhoneState= " + state) + " - Concurrent voice and data not allowed";
            }
            if (!z) {
                str2 = str2 + " - mInternalDataEnabled= false";
            }
            if (!isValidSubscriptionId) {
                str2 = str2 + " - defaultDataSelected= false";
            }
            if (this.mPhone.getServiceState().getDataRoaming() && !getDataOnRoamingEnabled()) {
                str2 = str2 + " - Roaming and data roaming not enabled";
            }
            if (this.mIsPsRestricted) {
                str2 = str2 + " - mIsPsRestricted= true";
            }
            if (!desiredPowerState) {
                str2 = str2 + " - desiredPowerState= false";
            }
            log("isDataAllowed: not allowed due to" + str2);
        }
        return z3;
    }

    public boolean isDataPossible(String str) {
        boolean z = true;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext == null) {
            return false;
        }
        boolean z2 = (apnContext.isEnabled() && apnContext.getState() == State.FAILED) ? false : true;
        boolean z3 = apnContext.getApnType().equals("emergency") || isDataAllowed();
        if (!(z3 && z2)) {
            z = false;
        }
        if ((apnContext.getApnType().equals("default") || apnContext.getApnType().equals("ia")) && this.mPhone.getServiceState().getRilDataRadioTechnology() == 18 && !this.mWwanIwlanCoexistFlag) {
            log("Default data call activation not possible in iwlan.");
            z = false;
        }
        return z;
    }

    public boolean isDisconnected() {
        for (ApnContext isDisconnected : this.mApnContexts.values()) {
            if (!isDisconnected.isDisconnected()) {
                return false;
            }
        }
        return true;
    }

    public boolean isOnDemandDataPossible(String str) {
        boolean z = true;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        if (apnContext == null) {
            return false;
        }
        boolean z2 = (apnContext.isEnabled() && apnContext.getState() == State.FAILED) ? false : true;
        boolean z3 = this.mUserDataEnabled;
        if ("mms".equals(str)) {
            z3 = this.mPhone.getContext().getResources().getBoolean(17957024);
            log("isOnDemandDataPossible MobileDataEnabled override = " + z3);
            z3 = this.mUserDataEnabled || z3;
        }
        if (!(z2 && z3)) {
            z = false;
        }
        log("isOnDemandDataPossible, possible =" + z + ", apnContext = " + apnContext);
        return z;
    }

    /* Access modifiers changed, original: protected */
    public boolean isPermanentFail(DcFailCause dcFailCause) {
        return dcFailCause.isPermanentFail() && !(this.mAttached.get() && dcFailCause == DcFailCause.SIGNAL_LOST);
    }

    /* Access modifiers changed, original: protected */
    public boolean isProvisioningApn(String str) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
        return apnContext != null ? apnContext.isProvisioningApn() : false;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + str);
    }

    /* Access modifiers changed, original: protected */
    public boolean mvnoMatches(IccRecords iccRecords, String str, String str2) {
        String imsi;
        if (str.equalsIgnoreCase("spn")) {
            if (iccRecords.getServiceProviderName() != null && iccRecords.getServiceProviderName().equalsIgnoreCase(str2)) {
                return true;
            }
        } else if (str.equalsIgnoreCase("imsi")) {
            imsi = iccRecords.getIMSI();
            if (imsi != null && imsiMatches(str2, imsi)) {
                return true;
            }
        } else if (str.equalsIgnoreCase("gid")) {
            imsi = iccRecords.getGid1();
            int length = str2.length();
            if (imsi != null && imsi.length() >= length && imsi.substring(0, length).equalsIgnoreCase(str2)) {
                return true;
            }
        }
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        this.mFailFast = false;
        this.mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    /* Access modifiers changed, original: protected */
    public void notifyDataConnection(String str) {
        log("notifyDataConnection: reason=" + str);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if ((this.mAttached.get() || !this.mOosIsDisconnect) && apnContext.isReady()) {
                log("notifyDataConnection: type:" + apnContext.getApnType());
                this.mPhone.notifyDataConnection(str != null ? str : apnContext.getReason(), apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(str);
    }

    /* Access modifiers changed, original: protected */
    public void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        Iterator it = this.mDisconnectAllCompleteMsgList.iterator();
        while (it.hasNext()) {
            ((Message) it.next()).sendToTarget();
        }
        this.mDisconnectAllCompleteMsgList.clear();
    }

    /* Access modifiers changed, original: protected */
    public void notifyOffApnsOfAvailability(String str) {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if ((!this.mAttached.get() && this.mOosIsDisconnect) || !apnContext.isReady()) {
                this.mPhone.notifyDataConnection(str != null ? str : apnContext.getReason(), apnContext.getApnType(), DataState.DISCONNECTED);
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void onCleanUpAllConnections(String str) {
        cleanUpAllConnections(true, str);
    }

    /* Access modifiers changed, original: protected */
    public void onCleanUpConnection(boolean z, int i, String str) {
        log("onCleanUpConnection");
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(i));
        if (apnContext != null) {
            apnContext.setReason(str);
            cleanUpConnection(z, apnContext);
        }
    }

    /* Access modifiers changed, original: protected */
    public void onDataConnectionDetached() {
        log("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
        this.mAttached.set(false);
    }

    /* Access modifiers changed, original: protected */
    public void onDataSetupComplete(AsyncResult asyncResult) {
        DcFailCause dcFailCause = DcFailCause.UNKNOWN;
        if (asyncResult.userObj instanceof ApnContext) {
            boolean z;
            ApnContext apnContext = (ApnContext) asyncResult.userObj;
            String apnType;
            if (asyncResult.exception == null) {
                DcAsyncChannel dcAc = apnContext.getDcAc();
                if (dcAc == null) {
                    log("onDataSetupComplete: no connection to DC, handle as error");
                    dcFailCause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                    z = true;
                } else {
                    ApnSetting apnSetting = apnContext.getApnSetting();
                    log("onDataSetupComplete: success apn=" + (apnSetting == null ? "unknown" : apnSetting.apn));
                    if (!(apnSetting == null || apnSetting.proxy == null || apnSetting.proxy.length() == 0)) {
                        try {
                            String str = apnSetting.port;
                            if (TextUtils.isEmpty(str)) {
                                str = "8080";
                            }
                            dcAc.setLinkPropertiesHttpProxySync(new ProxyInfo(apnSetting.proxy, Integer.parseInt(str), null));
                        } catch (NumberFormatException e) {
                            loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" + apnSetting.port + "): " + e);
                        }
                    }
                    if (TextUtils.equals(apnContext.getApnType(), "default")) {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                        if (this.mCanSetPreferApn && this.mPreferredApn == null) {
                            log("onDataSetupComplete: PREFERED APN is null");
                            this.mPreferredApn = apnSetting;
                            if (this.mPreferredApn != null) {
                                setPreferredApn(this.mPreferredApn.id);
                            }
                        }
                    } else {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    }
                    if (TelBrand.IS_KDDI && this.mEnableApnCarNavi && apnContext.getState() == State.DISCONNECTING) {
                        log("onDataSetupComplete: type=" + apnContext.getApnType() + " Do not change CONNECTED state during DISCONNECTING state when CarNavi");
                    } else {
                        apnContext.setState(State.CONNECTED);
                    }
                    boolean isProvisioningApn = apnContext.isProvisioningApn();
                    ConnectivityManager from = ConnectivityManager.from(this.mPhone.getContext());
                    if (this.mProvisionBroadcastReceiver != null) {
                        this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
                        this.mProvisionBroadcastReceiver = null;
                    }
                    if (!isProvisioningApn || this.mIsProvisioning) {
                        from.setProvisioningNotificationVisible(false, 0, this.mProvisionActionName);
                        completeConnection(apnContext);
                    } else {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as mIsProvisioning:" + this.mIsProvisioning + " == false" + " && (isProvisioningApn:" + isProvisioningApn + " == true");
                        this.mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(from.getMobileProvisioningUrl(), TelephonyManager.getDefault().getNetworkOperatorName());
                        this.mPhone.getContext().registerReceiver(this.mProvisionBroadcastReceiver, new IntentFilter(this.mProvisionActionName));
                        from.setProvisioningNotificationVisible(true, 0, this.mProvisionActionName);
                        setRadio(false);
                        Intent intent = new Intent("android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN");
                        intent.putExtra(Carriers.APN, apnContext.getApnSetting().apn);
                        intent.putExtra("apnType", apnContext.getApnType());
                        apnType = apnContext.getApnType();
                        LinkProperties linkProperties = getLinkProperties(apnType);
                        if (linkProperties != null) {
                            intent.putExtra("linkProperties", linkProperties);
                            String interfaceName = linkProperties.getInterfaceName();
                            if (interfaceName != null) {
                                intent.putExtra("iface", interfaceName);
                            }
                        }
                        NetworkCapabilities networkCapabilities = getNetworkCapabilities(apnType);
                        if (networkCapabilities != null) {
                            intent.putExtra("networkCapabilities", networkCapabilities);
                        }
                        this.mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                    }
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType() + ", reason:" + apnContext.getReason());
                    z = false;
                }
            } else {
                DcFailCause dcFailCause2 = (DcFailCause) asyncResult.result;
                ApnSetting apnSetting2 = apnContext.getApnSetting();
                apnType = apnSetting2 == null ? "unknown" : apnSetting2.apn;
                log(String.format("onDataSetupComplete: error apn=%s cause=%s", new Object[]{apnType, dcFailCause2}));
                if (dcFailCause2 == null) {
                    dcFailCause2 = DcFailCause.UNKNOWN;
                }
                if (dcFailCause2.isEventLoggable()) {
                    int cellLocationId = getCellLocationId();
                    EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL, new Object[]{Integer.valueOf(dcFailCause2.ordinal()), Integer.valueOf(cellLocationId), Integer.valueOf(TelephonyManager.getDefault().getNetworkType())});
                }
                apnSetting2 = apnContext.getApnSetting();
                this.mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(), apnContext.getApnType(), apnSetting2 != null ? apnSetting2.apn : "unknown", dcFailCause2.toString());
                if (isPermanentFail(dcFailCause2)) {
                    apnContext.decWaitingApnsPermFailCount();
                }
                apnContext.removeWaitingApn(apnContext.getApnSetting());
                log(String.format("onDataSetupComplete: WaitingApns.size=%d WaitingApnsPermFailureCountDown=%d", new Object[]{Integer.valueOf(apnContext.getWaitingApns().size()), Integer.valueOf(apnContext.getWaitingApnsPermFailCount())}));
                z = true;
            }
            if (z) {
                onDataSetupCompleteError(asyncResult);
            }
            if (!this.mInternalDataEnabled) {
                cleanUpAllConnections(null);
                return;
            }
            return;
        }
        throw new RuntimeException("onDataSetupComplete: No apnContext");
    }

    /* Access modifiers changed, original: protected */
    public void onDataSetupCompleteError(AsyncResult asyncResult) {
        if (asyncResult.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) asyncResult.userObj;
            if (apnContext.getWaitingApns().isEmpty()) {
                apnContext.setState(State.FAILED);
                this.mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());
                apnContext.setDataConnectionAc(null);
                if (apnContext.getWaitingApnsPermFailCount() == 0) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                    return;
                }
                int apnDelay = getApnDelay(Phone.REASON_APN_FAILED);
                log("onDataSetupCompleteError: Not all APN's had permanent failures delay=" + apnDelay);
                startAlarmForRestartTrySetup(apnDelay, apnContext);
                return;
            }
            log("onDataSetupCompleteError: Try next APN");
            apnContext.setState(State.SCANNING);
            startAlarmForReconnect(getApnDelay(Phone.REASON_APN_FAILED), apnContext);
            return;
        }
        throw new RuntimeException("onDataSetupCompleteError: No apnContext");
    }

    /* Access modifiers changed, original: protected */
    public void onDisconnectDcRetrying(int i, AsyncResult asyncResult) {
        if (asyncResult.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) asyncResult.userObj;
            apnContext.setState(State.RETRYING);
            log("onDisconnectDcRetrying: apnContext=" + apnContext);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            return;
        }
        loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
    }

    /* Access modifiers changed, original: protected */
    public void onDisconnectDone(int i, AsyncResult asyncResult) {
        if (asyncResult.userObj instanceof ApnContext) {
            boolean z;
            ApnContext apnContext = (ApnContext) asyncResult.userObj;
            log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            for (ApnContext state : this.mApnContexts.values()) {
                if (state.getState() == State.CONNECTED) {
                    z = false;
                    break;
                }
            }
            z = true;
            if (z) {
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
            if (this.mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
                SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                log("onDisconnectDone: attached, ready and retry after disconnect");
                if (TelBrand.IS_KDDI && this.mEnableApnCarNavi) {
                    startAlarmForReconnect(10000, apnContext);
                } else {
                    startAlarmForReconnect(getApnDelay(apnContext.getReason()), apnContext);
                }
            } else {
                z = this.mPhone.getContext().getResources().getBoolean(17956984);
                if (apnContext.isProvisioningApn() && z) {
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

    /* Access modifiers changed, original: protected */
    public void onEnableApn(int i, int i2) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(i));
        if (TelBrand.IS_KDDI) {
            if (i2 == 1 && this.RetryEnableApn < 100) {
                for (ApnContext apnContext2 : this.mApnContexts.values()) {
                    if (apnContext2.isEnabled() && apnContext2.getState() == State.DISCONNECTING) {
                        log("onEnableApn : " + apnContext2.getApnType() + " is Disconnecting!!!");
                        Message obtainMessage = obtainMessage(270349);
                        obtainMessage.arg1 = i;
                        obtainMessage.arg2 = i2;
                        sendMessageDelayed(obtainMessage, 200);
                        this.RetryEnableApn++;
                        return;
                    }
                }
            }
            this.RetryEnableApn = 0;
        }
        if (apnContext == null) {
            loge("onEnableApn(" + i + ", " + i2 + "): NO ApnContext");
            return;
        }
        log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        applyNewState(apnContext, i2 == 1, apnContext.getDependencyMet());
    }

    /* Access modifiers changed, original: protected */
    public void onRadioAvailable() {
        log("onRadioAvailable");
        if (this.mPhone.getSimulatedRadioControl() != null) {
            notifyDataConnection(null);
            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null && iccRecords.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }
        if (getOverallState() != State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    /* Access modifiers changed, original: protected */
    public void onRadioOffOrNotAvailable() {
        this.mReregisterOnReconnectFailure = false;
        if (this.mPhone.getSimulatedRadioControl() != null) {
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    /* Access modifiers changed, original: protected */
    public void onRoamingOff() {
        log("onRoamingOff");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (!this.mUserDataEnabled) {
            return;
        }
        if (getDataOnRoamingEnabled()) {
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
            return;
        }
        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
        setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
    }

    /* Access modifiers changed, original: protected */
    public void onRoamingOn() {
        log("onRoamingOn");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (!this.mUserDataEnabled) {
            return;
        }
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

    /* Access modifiers changed, original: protected */
    public void onSetDependencyMet(String str, boolean z) {
        if (!"hipri".equals(str)) {
            ApnContext apnContext = (ApnContext) this.mApnContexts.get(str);
            if (apnContext == null) {
                loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" + str + ", " + z + ")");
                return;
            }
            applyNewState(apnContext, apnContext.isEnabled(), z);
            if ("default".equals(str)) {
                apnContext = (ApnContext) this.mApnContexts.get("hipri");
                if (apnContext != null) {
                    applyNewState(apnContext, apnContext.isEnabled(), z);
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void onSetInternalDataEnabled(boolean z) {
        log("onSetInternalDataEnabled: enabled=" + z);
        onSetInternalDataEnabled(z, null);
    }

    /* Access modifiers changed, original: protected */
    public void onSetInternalDataEnabled(boolean z, Message message) {
        log("onSetInternalDataEnabled: enabled=" + z);
        Object obj = 1;
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = z;
            if (z) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                obj = null;
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null, message);
            }
        }
        if (obj != null && message != null) {
            message.sendToTarget();
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean onTrySetupData(ApnContext apnContext) {
        log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    /* Access modifiers changed, original: protected */
    public boolean onTrySetupData(String str) {
        log("onTrySetupData: reason=" + str);
        setupDataOnConnectableApns(str);
        return true;
    }

    /* Access modifiers changed, original: protected */
    public boolean onUpdateIcc() {
        if (this.mUiccController == null || !SubscriptionManager.isValidSubscriptionId(this.mPhone.getSubId())) {
            loge("onUpdateIcc: mUiccController is null. Error!");
            return false;
        }
        updateSimRecords();
        int familyFromRadioTechnology = UiccController.getFamilyFromRadioTechnology(this.mPhone.getServiceState().getRilDataRadioTechnology());
        if (this.mPhone.getPhoneType() == 1) {
            familyFromRadioTechnology = 1;
        }
        IccRecords uiccRecords = getUiccRecords(familyFromRadioTechnology);
        log("onUpdateIcc: newIccRecords " + (uiccRecords != null ? uiccRecords.getClass().getName() : null));
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == uiccRecords) {
            return false;
        }
        if (iccRecords != null) {
            log("Removing stale icc objects. " + (iccRecords != null ? iccRecords.getClass().getName() : null));
            iccRecords.unregisterForRecordsLoaded(this);
            this.mIccRecords.set(null);
        }
        if (uiccRecords == null) {
            return true;
        }
        log("New records found " + (uiccRecords != null ? uiccRecords.getClass().getName() : null));
        this.mIccRecords.set(uiccRecords);
        uiccRecords.registerForRecordsLoaded(this, 270338, null);
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void onVoiceCallEnded() {
        log("onVoiceCallEnded");
        if (TelBrand.IS_DCM && this.mIsEpcPending) {
            this.mIsEpcPending = false;
            updateOemDataSettings();
        }
        this.mInVoiceCall = false;
        if (isConnected()) {
            if (this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                resetPollStats();
            } else {
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            }
        }
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    /* Access modifiers changed, original: protected */
    public void onVoiceCallStarted() {
        log("onVoiceCallStarted");
        this.mInVoiceCall = true;
        if (isConnected() && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    public void registerForAllDataDisconnected(Handler handler, int i, Object obj) {
        this.mAllDataDisconnectedRegistrants.addUnique(handler, i, obj);
        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    /* Access modifiers changed, original: protected */
    public void registerForAllEvents() {
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

    public void resetDunProfiles() {
        int i = 1;
        if (TelBrand.IS_DCM) {
            updateDunProfileName(1, "IP", "dcmtrg.ne.jp");
            for (i = 2; i <= 10; i++) {
                updateDunProfileName(i, "IP", "LKvkWgIeq.o8s");
            }
            return;
        }
        while (i <= 10) {
            updateDunProfileName(i, "IP", "LKvkWgIeq.o8s");
            i++;
        }
    }

    /* Access modifiers changed, original: protected */
    public void restartRadio() {
        log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        this.mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0")) + 1));
    }

    public void setDataAllowed(boolean z, Message message) {
        log("setDataAllowed: enable=" + z);
        mIsCleanupRequired = !z;
        this.mPhone.mCi.setDataAllowed(z, message);
        this.mInternalDataEnabled = z;
    }

    public void setImsRegistrationState(boolean z) {
        log("setImsRegistrationState - mImsRegistrationState(before): " + this.mImsRegistrationState + ", registered(current) : " + z);
        if (this.mPhone != null) {
            ServiceStateTracker serviceStateTracker = this.mPhone.getServiceStateTracker();
            if (serviceStateTracker != null) {
                serviceStateTracker.setImsRegistrationState(z);
            }
        }
    }

    public boolean setInternalDataEnabled(boolean z) {
        return setInternalDataEnabled(z, null);
    }

    public boolean setInternalDataEnabled(boolean z, Message message) {
        log("setInternalDataEnabled(" + z + ")");
        Message obtainMessage = obtainMessage(270363, message);
        obtainMessage.arg1 = z ? 1 : 0;
        sendMessage(obtainMessage);
        return true;
    }

    public boolean setInternalDataEnabledFlag(boolean z) {
        log("setInternalDataEnabledFlag(" + z + ")");
        if (this.mInternalDataEnabled != z) {
            this.mInternalDataEnabled = z;
        }
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void setState(State state) {
        log("setState should not be used in GSM" + state);
    }

    /* Access modifiers changed, original: protected */
    public void setupDataAfterDdsSwitchIfPossible() {
        log("setupDataAfterDdsSwitchIfPossible: Attached = " + this.mAttached.get());
        if (this.mAttached.get()) {
            Iterator it = this.mPrioritySortedApnContexts.iterator();
            while (it.hasNext()) {
                ApnContext apnContext = (ApnContext) it.next();
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

    /* Access modifiers changed, original: protected */
    public void setupDataForSinglePdnArbitration(String str) {
        log("setupDataForSinglePdn: reason = " + str + " isDisconnected = " + isDisconnected());
        if (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isDisconnected() && !Phone.REASON_SINGLE_PDN_ARBITRATION.equals(str) && !Phone.REASON_RADIO_TURNED_OFF.equals(str)) {
            setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
        }
    }

    /* Access modifiers changed, original: protected */
    public void startNetStatPoll() {
        if (this.mIsPhysicalLinkUp && !this.mIsPsRestricted && this.mPhone.getServiceStateTracker().getCurrentDataConnectionState() == 0) {
            super.startNetStatPoll();
        }
    }

    /* Access modifiers changed, original: protected */
    public void supplyMessenger() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        connectivityManager.supplyMessenger(0, new Messenger(this));
        connectivityManager.supplyMessenger(2, new Messenger(this));
        connectivityManager.supplyMessenger(3, new Messenger(this));
        connectivityManager.supplyMessenger(4, new Messenger(this));
        connectivityManager.supplyMessenger(5, new Messenger(this));
        connectivityManager.supplyMessenger(10, new Messenger(this));
        connectivityManager.supplyMessenger(11, new Messenger(this));
        connectivityManager.supplyMessenger(12, new Messenger(this));
        connectivityManager.supplyMessenger(15, new Messenger(this));
    }

    public void unregisterForAllDataDisconnected(Handler handler) {
        this.mAllDataDisconnectedRegistrants.remove(handler);
    }

    /* Access modifiers changed, original: protected */
    public void unregisterForAllEvents() {
        this.mPhone.mCi.unregisterForAvailable(this);
        this.mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.unregisterForRecordsLoaded(this);
            this.mIccRecords.set(null);
        }
        iccRecords = (IccRecords) this.mSimRecords.get();
        if (iccRecords != null) {
            iccRecords.unregisterForRecordsLoaded(this.mSimRecordsLoadedHandler);
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

    public void update() {
        boolean z = true;
        log("update sub = " + this.mPhone.getSubId());
        log("update(): Active DDS, register for all events now!");
        onUpdateIcc();
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data" + this.mPhone.getPhoneId(), 1) != 1) {
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

    public void updateDunProfileName(int i, String str, String str2) {
        int length = "QOEMHOOK".length();
        String num = Integer.toString(1);
        String num2 = Integer.toString(i);
        if (str2 == null) {
            str2 = "";
        }
        String num3 = Integer.toString(0);
        if (str == null) {
            str = "IP";
        }
        int length2 = ((((((((((((num.length() + 1) + num2.length()) + 1) + str2.length()) + 1) + "".length()) + 1) + "".length()) + 1) + num3.length()) + 1) + str.length()) + 1;
        byte[] bArr = new byte[((length + 8) + length2)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put("QOEMHOOK".getBytes());
        wrap.putInt(591832);
        wrap.putInt(length2);
        wrap.put(num.getBytes());
        wrap.put((byte) 0);
        wrap.put(num2.getBytes());
        wrap.put((byte) 0);
        wrap.put(str2.getBytes());
        wrap.put((byte) 0);
        wrap.put("".getBytes());
        wrap.put((byte) 0);
        wrap.put("".getBytes());
        wrap.put((byte) 0);
        wrap.put(num3.getBytes());
        wrap.put((byte) 0);
        wrap.put(str.getBytes());
        wrap.put((byte) 0);
        this.mPhone.mCi.invokeOemRilRequestRaw(bArr, null);
    }

    public void updateRecords() {
        onUpdateIcc();
    }
}
