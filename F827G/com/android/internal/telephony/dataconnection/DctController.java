package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings.Global;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.SparseArray;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.SubscriptionController.OnDemandDdsLockNotifier;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel.RequestInfo;
import com.android.internal.util.AsyncChannel;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map.Entry;

public class DctController extends Handler {
    private static final boolean DBG = true;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1;
    private static final int EVENT_DATA_ATTACHED = 500;
    private static final int EVENT_DATA_DETACHED = 600;
    private static final int EVENT_DELAYED_RETRY = 3;
    private static final int EVENT_EXECUTE_ALL_REQUESTS = 102;
    private static final int EVENT_EXECUTE_REQUEST = 101;
    private static final int EVENT_LEGACY_SET_DATA_SUBSCRIPTION = 4;
    private static final int EVENT_PROCESS_REQUESTS = 100;
    private static final int EVENT_RELEASE_ALL_REQUESTS = 104;
    private static final int EVENT_RELEASE_REQUEST = 103;
    private static final int EVENT_SET_DATA_ALLOW_DONE = 2;
    private static final int EVENT_SET_DATA_ALLOW_FALSE_DONE = 6;
    private static final int EVENT_SET_DATA_ALLOW_TRUE_DONE = 5;
    private static final int EVENT_START_DDS_SWITCH = 105;
    private static final String LOG_TAG = "DctController";
    private static boolean isOnDemandDdsSwitchInProgress = false;
    private static DctController sDctController;
    private final int ATTACH_RETRY_DELAY = 10000;
    private final int MAX_RETRY_FOR_ATTACH = 6;
    private BroadcastReceiver defaultDdsBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            DctController.logd("got ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED, new DDS = " + intent.getIntExtra("subscription", -1));
            DctController.this.updateSubIdAndCapability();
        }
    };
    private Context mContext;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private Handler[] mDcSwitchStateHandler;
    private DcSwitchStateMachine[] mDcSwitchStateMachine;
    private AsyncChannel mDdsSwitchPropService;
    private DdsSwitchSerializerHandler mDdsSwitchSerializer;
    private boolean mIsDdsSwitchCompleted = true;
    private NetworkFactory[] mNetworkFactory;
    private Messenger[] mNetworkFactoryMessenger;
    private NetworkCapabilities[] mNetworkFilter;
    private RegistrantList mNotifyDefaultDataSwitchInfo = new RegistrantList();
    private RegistrantList mNotifyOnDemandDataSwitchInfo = new RegistrantList();
    private RegistrantList mNotifyOnDemandPsAttach = new RegistrantList();
    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean z) {
            DctController.logd("Settings change");
            DctController.this.onSettingsChange();
        }
    };
    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener = new OnSubscriptionsChangedListener() {
        public void onSubscriptionsChanged() {
            DctController.this.onSubInfoReady();
        }
    };
    private int mPhoneNum;
    private PhoneProxy[] mPhones;
    private HashMap<Integer, RequestInfo> mRequestInfos = new HashMap();
    private Handler mRspHandler = new Handler() {
        public void handleMessage(Message message) {
            if (message.what >= DctController.EVENT_DATA_DETACHED) {
                DctController.logd("EVENT_PHONE" + ((message.what - 600) + 1) + "_DATA_DETACH.");
                DctController.this.mDcSwitchAsyncChannel[message.what - 600].notifyDataDetached();
            } else if (message.what >= DctController.EVENT_DATA_ATTACHED) {
                DctController.logd("EVENT_PHONE" + ((message.what - 500) + 1) + "_DATA_ATTACH.");
                DctController.this.mDcSwitchAsyncChannel[message.what - 500].notifyDataAttached();
            }
        }
    };
    private SubscriptionController mSubController = SubscriptionController.getInstance();
    private SubscriptionManager mSubMgr;

    class DdsSwitchSerializerHandler extends Handler {
        static final String TAG = "DdsSwitchSerializer";

        public DdsSwitchSerializerHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            switch (message.what) {
                case DctController.EVENT_START_DDS_SWITCH /*105*/:
                    Rlog.d(TAG, "EVENT_START_DDS_SWITCH");
                    try {
                        synchronized (this) {
                            while (!DctController.this.mIsDdsSwitchCompleted) {
                                Rlog.d(TAG, "DDS switch in progress, wait");
                                wait();
                            }
                            Rlog.d(TAG, "Locked!");
                            DctController.this.mIsDdsSwitchCompleted = false;
                        }
                        NetworkRequest networkRequest = (NetworkRequest) message.obj;
                        Rlog.d(TAG, "start the DDS switch for req " + networkRequest);
                        int subIdFromNetworkRequest = DctController.this.mSubController.getSubIdFromNetworkRequest(networkRequest);
                        if (subIdFromNetworkRequest == DctController.this.mSubController.getCurrentDds()) {
                            Rlog.d(TAG, "No change in DDS, respond back");
                            DctController.this.mIsDdsSwitchCompleted = true;
                            DctController.this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult(null, networkRequest, null));
                            return;
                        }
                        int phoneId = DctController.this.mSubController.getPhoneId(subIdFromNetworkRequest);
                        int phoneId2 = DctController.this.mSubController.getPhoneId(DctController.this.mSubController.getCurrentDds());
                        Phone activePhone = DctController.this.mPhones[phoneId2].getActivePhone();
                        DcTrackerBase dcTrackerBase = ((PhoneBase) activePhone).mDcTracker;
                        dcTrackerBase.setDataAllowed(false, Message.obtain(DctController.this, 6, new SwitchInfo(new Integer(phoneId2).intValue(), networkRequest, false, false)));
                        if (activePhone.getPhoneType() == 2) {
                            dcTrackerBase.cleanUpAllConnections("Ondemand DDS switch");
                        }
                        DctController.this.mPhones[phoneId2].registerForAllDataDisconnected(DctController.sDctController, 1, new SwitchInfo(new Integer(phoneId).intValue(), networkRequest, false, false));
                        DctController.isOnDemandDdsSwitchInProgress = true;
                        return;
                    } catch (Exception e) {
                        Rlog.d(TAG, "Exception while serializing the DDS switch request , e=" + e);
                        return;
                    }
                default:
                    return;
            }
        }

        public boolean isLocked() {
            boolean z = true;
            synchronized (this) {
                Rlog.d(TAG, "isLocked = " + (!DctController.this.mIsDdsSwitchCompleted));
                if (DctController.this.mIsDdsSwitchCompleted) {
                    z = false;
                }
            }
            return z;
        }

        public void unLock() {
            Rlog.d(TAG, "unLock the DdsSwitchSerializer");
            synchronized (this) {
                DctController.this.mIsDdsSwitchCompleted = true;
                Rlog.d(TAG, "unLocked the DdsSwitchSerializer");
                notifyAll();
            }
        }
    }

    private class SwitchInfo {
        public boolean mIsDefaultDataSwitchRequested;
        public boolean mIsOnDemandPsAttachRequested;
        public NetworkRequest mNetworkRequest;
        public int mPhoneId;
        private int mRetryCount = 0;

        public SwitchInfo(int i, NetworkRequest networkRequest, boolean z, boolean z2) {
            this.mPhoneId = i;
            this.mNetworkRequest = networkRequest;
            this.mIsDefaultDataSwitchRequested = z;
            this.mIsOnDemandPsAttachRequested = z2;
        }

        public SwitchInfo(int i, boolean z) {
            this.mPhoneId = i;
            this.mNetworkRequest = null;
            this.mIsDefaultDataSwitchRequested = z;
        }

        public void incRetryCount() {
            this.mRetryCount++;
        }

        public boolean isRetryPossible() {
            return this.mRetryCount < 6;
        }

        public String toString() {
            return "SwitchInfo[phoneId = " + this.mPhoneId + ", NetworkRequest =" + this.mNetworkRequest + ", isDefaultSwitchRequested = " + this.mIsDefaultDataSwitchRequested + ", isOnDemandPsAttachRequested = " + this.mIsOnDemandPsAttachRequested + ", RetryCount = " + this.mRetryCount;
        }
    }

    private class TelephonyNetworkFactory extends NetworkFactory {
        private SparseArray<NetworkRequest> mDdsRequests = new SparseArray();
        private NetworkCapabilities mNetworkCapabilities;
        private final SparseArray<NetworkRequest> mPendingReq = new SparseArray();
        private Phone mPhone;

        public TelephonyNetworkFactory(Looper looper, Context context, String str, Phone phone, NetworkCapabilities networkCapabilities) {
            super(looper, context, str, networkCapabilities);
            this.mPhone = phone;
            this.mNetworkCapabilities = networkCapabilities;
            log("NetworkCapabilities: " + networkCapabilities);
        }

        private boolean isNetworkRequestForInternet(NetworkRequest networkRequest) {
            boolean hasCapability = networkRequest.networkCapabilities.hasCapability(12);
            log("Is the request for Internet = " + hasCapability);
            return hasCapability;
        }

        private boolean isValidRequest(NetworkRequest networkRequest) {
            return networkRequest.networkCapabilities.getCapabilities().length > 0;
        }

        private void removeRequestFromList(SparseArray<NetworkRequest> sparseArray, NetworkRequest networkRequest) {
            NetworkRequest networkRequest2 = (NetworkRequest) sparseArray.get(networkRequest.requestId);
            if (networkRequest2 != null) {
                log("Removing request = " + networkRequest2);
                sparseArray.remove(networkRequest.requestId);
                String access$1100 = DctController.this.apnForNetworkRequest(networkRequest2);
                DcTrackerBase dcTrackerBase = ((PhoneBase) this.mPhone).mDcTracker;
                if (dcTrackerBase.isApnSupported(access$1100)) {
                    dcTrackerBase.decApnRefCount(access$1100);
                } else {
                    log("Unsupported APN");
                }
            }
        }

        private void removeRequestIfFound(NetworkRequest networkRequest) {
            log("Release the request from dds queue, if found");
            removeRequestFromList(this.mDdsRequests, networkRequest);
            if (isNetworkRequestForInternet(networkRequest)) {
                String access$1100 = DctController.this.apnForNetworkRequest(networkRequest);
                DcTrackerBase dcTrackerBase = ((PhoneBase) this.mPhone).mDcTracker;
                if (dcTrackerBase.isApnSupported(access$1100)) {
                    dcTrackerBase.decApnRefCount(access$1100);
                    return;
                } else {
                    log("Unsupported APN");
                    return;
                }
            }
            SubscriptionController.getInstance().stopOnDemandDataSubscriptionRequest(networkRequest);
        }

        private void requestOnDemandDataSubscriptionLock(NetworkRequest networkRequest) {
            if (!isNetworkRequestForInternet(networkRequest)) {
                SubscriptionController instance = SubscriptionController.getInstance();
                log("requestOnDemandDataSubscriptionLock for request = " + networkRequest);
                instance.startOnDemandDataSubscriptionRequest(networkRequest);
            }
        }

        public void evalPendingRequest() {
            log("evalPendingRequest, pending request size is " + this.mPendingReq.size());
            for (int i = 0; i < this.mPendingReq.size(); i++) {
                NetworkRequest networkRequest = (NetworkRequest) this.mPendingReq.get(this.mPendingReq.keyAt(i));
                log("evalPendingRequest: request = " + networkRequest);
                this.mPendingReq.remove(networkRequest.requestId);
                needNetworkFor(networkRequest, 0);
            }
        }

        /* Access modifiers changed, original: protected */
        public void log(String str) {
            Rlog.d(DctController.LOG_TAG, "[TNF " + this.mPhone.getSubId() + "]" + str);
        }

        /* Access modifiers changed, original: protected */
        public void needNetworkFor(NetworkRequest networkRequest, int i) {
            log("Cellular needs Network for " + networkRequest);
            int subId = this.mPhone.getSubId();
            if (SubscriptionManager.isUsableSubIdValue(subId) && SubscriptionController.getInstance().getSubState(subId) == 1) {
                SubscriptionController instance = SubscriptionController.getInstance();
                log("subController = " + instance);
                int defaultDataSubId = instance.getDefaultDataSubId();
                int subIdFromNetworkRequest = instance.getSubIdFromNetworkRequest(networkRequest);
                log("CurrentDds = " + defaultDataSubId);
                log("mySubId = " + subId);
                log("Requested networkSpecifier = " + subIdFromNetworkRequest);
                log("my networkSpecifier = " + this.mNetworkCapabilities.getNetworkSpecifier());
                if (!DctController.this.isActiveSubId(defaultDataSubId)) {
                    log("Can't handle any network request now, currentDds not ready.");
                    this.mPendingReq.put(networkRequest.requestId, networkRequest);
                    return;
                } else if (subIdFromNetworkRequest != subId) {
                    log("requestedSpecifier is not same as mysubId. Bail out.");
                    return;
                } else if (defaultDataSubId != subIdFromNetworkRequest) {
                    log("This request would result in DDS switch");
                    log("Requested DDS switch to subId = " + subIdFromNetworkRequest);
                    this.mDdsRequests.put(networkRequest.requestId, networkRequest);
                    requestOnDemandDataSubscriptionLock(networkRequest);
                    return;
                } else if (isNetworkRequestForInternet(networkRequest)) {
                    log("Activating internet request on subId = " + subId);
                    String access$1100 = DctController.this.apnForNetworkRequest(networkRequest);
                    DcTrackerBase dcTrackerBase = ((PhoneBase) this.mPhone).mDcTracker;
                    if (dcTrackerBase.isApnSupported(access$1100)) {
                        DctController.this.requestNetwork(networkRequest, dcTrackerBase.getApnPriority(access$1100));
                        return;
                    } else {
                        log("Unsupported APN");
                        return;
                    }
                } else if (isValidRequest(networkRequest)) {
                    this.mDdsRequests.put(networkRequest.requestId, networkRequest);
                    requestOnDemandDataSubscriptionLock(networkRequest);
                    return;
                } else {
                    log("Bogus request req = " + networkRequest);
                    return;
                }
            }
            log("Sub Info has not been ready, pending request.");
            this.mPendingReq.put(networkRequest.requestId, networkRequest);
        }

        public void processPendingNetworkRequests(NetworkRequest networkRequest) {
            int i = 0;
            while (true) {
                int i2 = i;
                if (i2 < this.mDdsRequests.size()) {
                    NetworkRequest networkRequest2 = (NetworkRequest) this.mDdsRequests.valueAt(i2);
                    if (networkRequest2.equals(networkRequest)) {
                        log("Found pending request in ddsRequest list = " + networkRequest2);
                        String access$1100 = DctController.this.apnForNetworkRequest(networkRequest2);
                        DcTrackerBase dcTrackerBase = ((PhoneBase) this.mPhone).mDcTracker;
                        if (dcTrackerBase.isApnSupported(access$1100)) {
                            dcTrackerBase.incApnRefCount(access$1100);
                        } else {
                            log("Unsupported APN");
                        }
                    }
                    i = i2 + 1;
                } else {
                    return;
                }
            }
        }

        public void registerOnDemandDdsCallback() {
            SubscriptionController.getInstance().registerForOnDemandDdsLockNotification(this.mPhone.getSubId(), new OnDemandDdsLockNotifier() {
                public void notifyOnDemandDdsLockGranted(NetworkRequest networkRequest) {
                    TelephonyNetworkFactory.this.log("Got the tempDds lock for the request = " + networkRequest);
                    TelephonyNetworkFactory.this.processPendingNetworkRequests(networkRequest);
                }
            });
        }

        public void releaseAllNetworkRequests() {
            log("releaseAllNetworkRequests");
            SubscriptionController instance = SubscriptionController.getInstance();
            int i = 0;
            while (true) {
                int i2 = i;
                if (i2 < this.mDdsRequests.size()) {
                    NetworkRequest networkRequest = (NetworkRequest) this.mDdsRequests.valueAt(i2);
                    if (networkRequest != null) {
                        log("Removing request = " + networkRequest);
                        instance.stopOnDemandDataSubscriptionRequest(networkRequest);
                        this.mDdsRequests.remove(networkRequest.requestId);
                    }
                    i = i2 + 1;
                } else {
                    return;
                }
            }
        }

        /* Access modifiers changed, original: protected */
        public void releaseNetworkFor(NetworkRequest networkRequest) {
            log("Cellular releasing Network for " + networkRequest);
            if (this.mPendingReq.get(networkRequest.requestId) != null) {
                log("Remove the request from pending list.");
                this.mPendingReq.remove(networkRequest.requestId);
            } else if (((NetworkRequest) this.mDdsRequests.get(networkRequest.requestId)) != null) {
                removeRequestIfFound(networkRequest);
            } else if (DctController.this.getRequestPhoneId(networkRequest) != this.mPhone.getPhoneId()) {
                log("Request not release");
            } else if (((PhoneBase) this.mPhone).mDcTracker.isApnSupported(DctController.this.apnForNetworkRequest(networkRequest))) {
                DctController.this.releaseNetwork(networkRequest);
            } else {
                log("Unsupported APN");
            }
        }

        public void updateNetworkCapability() {
            int subId = this.mPhone.getSubId();
            log("update networkCapabilites for subId = " + subId);
            this.mNetworkCapabilities.setNetworkSpecifier("" + subId);
            if (subId > 0 && SubscriptionController.getInstance().getSubState(subId) == 1 && subId == SubscriptionController.getInstance().getDefaultDataSubId()) {
                log("INTERNET capability is with subId = " + subId);
                this.mNetworkCapabilities.addCapability(12);
            } else {
                log("INTERNET capability is removed from subId = " + subId);
                this.mNetworkCapabilities.removeCapability(12);
            }
            setScoreFilter(50);
            registerOnDemandDdsCallback();
            log("Ready to handle network requests");
        }
    }

    private DctController(PhoneProxy[] phoneProxyArr, Looper looper) {
        super(looper);
        logd("DctController(): phones.length=" + phoneProxyArr.length);
        if (phoneProxyArr != null && phoneProxyArr.length != 0) {
            this.mPhoneNum = phoneProxyArr.length;
            this.mPhones = phoneProxyArr;
            this.mDcSwitchStateMachine = new DcSwitchStateMachine[this.mPhoneNum];
            this.mDcSwitchAsyncChannel = new DcSwitchAsyncChannel[this.mPhoneNum];
            this.mDcSwitchStateHandler = new Handler[this.mPhoneNum];
            this.mNetworkFactoryMessenger = new Messenger[this.mPhoneNum];
            this.mNetworkFactory = new NetworkFactory[this.mPhoneNum];
            this.mNetworkFilter = new NetworkCapabilities[this.mPhoneNum];
            for (int i = 0; i < this.mPhoneNum; i++) {
                this.mDcSwitchStateMachine[i] = new DcSwitchStateMachine(this.mPhones[i], "DcSwitchStateMachine-" + i, i);
                this.mDcSwitchStateMachine[i].start();
                this.mDcSwitchAsyncChannel[i] = new DcSwitchAsyncChannel(this.mDcSwitchStateMachine[i], i);
                this.mDcSwitchStateHandler[i] = new Handler();
                if (this.mDcSwitchAsyncChannel[i].fullyConnectSync(this.mPhones[i].getContext(), this.mDcSwitchStateHandler[i], this.mDcSwitchStateMachine[i].getHandler()) == 0) {
                    logd("DctController(phones): Connect success: " + i);
                } else {
                    loge("DctController(phones): Could not connect to " + i);
                }
                updatePhoneBaseForIndex(i, (PhoneBase) this.mPhones[i].getActivePhone());
            }
            this.mContext = this.mPhones[0].getContext();
            HandlerThread handlerThread = new HandlerThread("DdsSwitchSerializer");
            handlerThread.start();
            this.mDdsSwitchSerializer = new DdsSwitchSerializerHandler(handlerThread.getLooper());
            this.mContext.registerReceiver(this.defaultDdsBroadcastReceiver, new IntentFilter("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED"));
            this.mSubMgr = SubscriptionManager.from(this.mContext);
            this.mSubMgr.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
            this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("multi_sim_data_call"), false, this.mObserver);
        } else if (phoneProxyArr == null) {
            loge("DctController(phones): UNEXPECTED phones=null, ignore");
        } else {
            loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
        }
    }

    private String apnForNetworkRequest(NetworkRequest networkRequest) {
        int i = 0;
        int i2 = 1;
        NetworkCapabilities networkCapabilities = networkRequest.networkCapabilities;
        if (networkCapabilities.getTransportTypes().length > 0 && !networkCapabilities.hasTransport(0)) {
            return null;
        }
        String str;
        int i3;
        if (networkCapabilities.hasCapability(12)) {
            str = "default";
            i3 = 0;
        } else {
            i3 = -1;
            str = null;
        }
        if (networkCapabilities.hasCapability(0)) {
            if (str != null) {
                i = 1;
            }
            str = "mms";
            i3 = 2;
        }
        if (networkCapabilities.hasCapability(1)) {
            if (str != null) {
                i = 1;
            }
            str = "supl";
            i3 = 3;
        }
        if (networkCapabilities.hasCapability(2)) {
            if (str != null) {
                i = 1;
            }
            str = "dun";
            i3 = 4;
        }
        if (networkCapabilities.hasCapability(3)) {
            if (str != null) {
                i = 1;
            }
            str = "fota";
            i3 = 10;
        }
        if (networkCapabilities.hasCapability(4)) {
            if (str != null) {
                i = 1;
            }
            str = "ims";
            i3 = 11;
        }
        if (networkCapabilities.hasCapability(5)) {
            if (str != null) {
                i = 1;
            }
            str = "cbs";
            i3 = 12;
        }
        if (networkCapabilities.hasCapability(7)) {
            if (str != null) {
                i = 1;
            }
            str = "ia";
            i3 = 14;
        }
        if (networkCapabilities.hasCapability(8)) {
            if (str != null) {
                i = 1;
            }
            loge("RCS APN type not yet supported");
            str = null;
        }
        if (networkCapabilities.hasCapability(9)) {
            if (str != null) {
                i = 1;
            }
            loge("XCAP APN type not yet supported");
            str = null;
        }
        if (networkCapabilities.hasCapability(10)) {
            if (str == null) {
                i2 = i;
            }
            loge("EIMS APN type not yet supported");
            str = null;
        } else {
            i2 = i;
        }
        if (i2 != 0) {
            loge("Multiple apn types specified in request - result is unspecified!");
        }
        if (i3 != -1 && str != null) {
            return str;
        }
        loge("Unsupported NetworkRequest in Telephony: nr=" + networkRequest);
        return null;
    }

    private void doDetach(int i) {
        Phone activePhone = this.mPhones[i].getActivePhone();
        DcTrackerBase dcTrackerBase = ((PhoneBase) activePhone).mDcTracker;
        dcTrackerBase.setDataAllowed(false, null);
        if (activePhone.getPhoneType() == 2) {
            dcTrackerBase.cleanUpAllConnections("DDS switch");
        }
    }

    private int getDataConnectionFromSetting() {
        return SubscriptionManager.getPhoneId(this.mSubController.getDefaultDataSubId());
    }

    public static DctController getInstance() {
        if (sDctController != null) {
            return sDctController;
        }
        throw new RuntimeException("DctController.getInstance can't be called before makeDCTController()");
    }

    private int getRequestPhoneId(NetworkRequest networkRequest) {
        String networkSpecifier = networkRequest.networkCapabilities.getNetworkSpecifier();
        int defaultDataSubId = (networkSpecifier == null || networkSpecifier.equals("")) ? this.mSubController.getDefaultDataSubId() : Integer.parseInt(networkSpecifier);
        defaultDataSubId = this.mSubController.getPhoneId(defaultDataSubId);
        if (SubscriptionManager.isValidPhoneId(defaultDataSubId)) {
            return defaultDataSubId;
        }
        if (SubscriptionManager.isValidPhoneId(0)) {
            return 0;
        }
        throw new RuntimeException("Should not happen, no valid phoneId");
    }

    private int getTopPriorityRequestPhoneId() {
        int i;
        RequestInfo requestInfo = null;
        int i2 = -1;
        int i3 = 0;
        while (i3 < this.mPhoneNum) {
            i = i2;
            for (Object obj : this.mRequestInfos.keySet()) {
                RequestInfo requestInfo2 = (RequestInfo) this.mRequestInfos.get(obj);
                logd("selectExecPhone requestInfo = " + requestInfo2);
                if (getRequestPhoneId(requestInfo2.request) == i3 && i < requestInfo2.priority) {
                    i = requestInfo2.priority;
                    requestInfo = requestInfo2;
                }
            }
            i3++;
            i2 = i;
        }
        if (requestInfo != null) {
            i = getRequestPhoneId(requestInfo.request);
        } else {
            i = this.mSubController.getPhoneId(this.mSubController.getDefaultDataSubId());
            logd("getTopPriorityRequestPhoneId: RequestInfo list is empty, use Dds sub phone id");
        }
        logd("getTopPriorityRequestPhoneId = " + i + ", priority = " + i2);
        return i;
    }

    private void informDefaultDdsToPropServ(int i) {
        if (this.mDdsSwitchPropService != null) {
            logd("Inform OemHookDDS service of current DDS = " + i);
            this.mDdsSwitchPropService.sendMessageSynchronously(1, i, this.mPhoneNum);
            logd("OemHookDDS service finished");
            return;
        }
        logd("OemHookDds service not ready yet");
    }

    private static void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private static void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    public static DctController makeDctController(PhoneProxy[] phoneProxyArr, Looper looper) {
        if (sDctController == null) {
            logd("makeDctController: new DctController phones.length=" + phoneProxyArr.length);
            sDctController = new DctController(phoneProxyArr, looper);
            DdsScheduler.init();
        }
        logd("makeDctController: X sDctController=" + sDctController);
        return sDctController;
    }

    private void onExecuteAllRequests(int i) {
        logd("onExecuteAllRequests phoneId=" + i);
        for (Object obj : this.mRequestInfos.keySet()) {
            RequestInfo requestInfo = (RequestInfo) this.mRequestInfos.get(obj);
            if (getRequestPhoneId(requestInfo.request) == i) {
                onExecuteRequest(requestInfo);
            }
        }
    }

    private void onExecuteRequest(RequestInfo requestInfo) {
        logd("onExecuteRequest request=" + requestInfo);
        if (!requestInfo.executed) {
            requestInfo.executed = true;
            ((PhoneBase) this.mPhones[getRequestPhoneId(requestInfo.request)].getActivePhone()).mDcTracker.incApnRefCount(apnForNetworkRequest(requestInfo.request));
        }
    }

    private void onProcessRequest() {
        int topPriorityRequestPhoneId = getTopPriorityRequestPhoneId();
        int i = 0;
        while (i < this.mDcSwitchStateMachine.length) {
            if (!this.mDcSwitchAsyncChannel[i].isIdleSync()) {
                break;
            }
            i++;
        }
        i = -1;
        logd("onProcessRequest phoneId=" + topPriorityRequestPhoneId + ", activePhoneId=" + i);
        if (i == -1 || i == topPriorityRequestPhoneId) {
            for (Object obj : this.mRequestInfos.keySet()) {
                RequestInfo requestInfo = (RequestInfo) this.mRequestInfos.get(obj);
                if (getRequestPhoneId(requestInfo.request) == topPriorityRequestPhoneId && !requestInfo.executed) {
                    this.mDcSwitchAsyncChannel[topPriorityRequestPhoneId].connectSync(requestInfo);
                }
            }
            return;
        }
        this.mDcSwitchAsyncChannel[i].disconnectAllSync();
    }

    private void onReleaseAllRequests(int i) {
        logd("onReleaseAllRequests phoneId=" + i);
        for (Object obj : this.mRequestInfos.keySet()) {
            RequestInfo requestInfo = (RequestInfo) this.mRequestInfos.get(obj);
            if (getRequestPhoneId(requestInfo.request) == i) {
                onReleaseRequest(requestInfo);
            }
        }
    }

    private void onReleaseRequest(RequestInfo requestInfo) {
        logd("onReleaseRequest request=" + requestInfo);
        if (requestInfo != null && requestInfo.executed) {
            ((PhoneBase) this.mPhones[getRequestPhoneId(requestInfo.request)].getActivePhone()).mDcTracker.decApnRefCount(apnForNetworkRequest(requestInfo.request));
            requestInfo.executed = false;
        }
    }

    private void onSettingsChange() {
        int i;
        int i2 = 0;
        int defaultDataSubId = this.mSubController.getDefaultDataSubId();
        for (int i3 = 0; i3 < this.mDcSwitchStateMachine.length; i3++) {
            if (!this.mDcSwitchAsyncChannel[i3].isIdleSync()) {
                i = i3;
                break;
            }
        }
        i = -1;
        int[] subId = SubscriptionManager.getSubId(i);
        if (subId == null || subId.length == 0) {
            loge("onSettingsChange, subIds null or length 0 for activePhoneId " + i);
            return;
        }
        logd("onSettingsChange, data sub: " + defaultDataSubId + ", active data sub: " + subId[0]);
        if (subId[0] != defaultDataSubId) {
            for (Object obj : this.mRequestInfos.keySet()) {
                RequestInfo requestInfo = (RequestInfo) this.mRequestInfos.get(obj);
                String networkSpecifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
                if ((networkSpecifier == null || networkSpecifier.equals("")) && requestInfo.executed) {
                    String apnForNetworkRequest = apnForNetworkRequest(requestInfo.request);
                    logd("[setDataSubId] activePhoneId:" + i + ", subId =" + defaultDataSubId);
                    ((PhoneBase) this.mPhones[i].getActivePhone()).mDcTracker.decApnRefCount(apnForNetworkRequest);
                    requestInfo.executed = false;
                }
            }
        }
        while (i2 < this.mPhoneNum) {
            ((TelephonyNetworkFactory) this.mNetworkFactory[i2]).evalPendingRequest();
            i2++;
        }
        processRequests();
    }

    private void onSubInfoReady() {
        logd("onSubInfoReady mPhoneNum=" + this.mPhoneNum);
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mPhoneNum) {
                i = this.mPhones[i2].getSubId();
                logd("onSubInfoReady handle pending requests subId=" + i);
                this.mNetworkFilter[i2].setNetworkSpecifier(String.valueOf(i));
                ((TelephonyNetworkFactory) this.mNetworkFactory[i2]).registerOnDemandDdsCallback();
                ((TelephonyNetworkFactory) this.mNetworkFactory[i2]).evalPendingRequest();
                i = i2 + 1;
            } else {
                processRequests();
                return;
            }
        }
    }

    private void releaseAllNetworkRequests() {
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mPhoneNum) {
                ((TelephonyNetworkFactory) this.mNetworkFactory[i2]).releaseAllNetworkRequests();
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private int releaseNetwork(NetworkRequest networkRequest) {
        RequestInfo requestInfo = (RequestInfo) this.mRequestInfos.get(Integer.valueOf(networkRequest.requestId));
        logd("releaseNetwork request=" + networkRequest + ", requestInfo=" + requestInfo);
        this.mRequestInfos.remove(Integer.valueOf(networkRequest.requestId));
        releaseRequest(requestInfo);
        processRequests();
        return 1;
    }

    private int requestNetwork(NetworkRequest networkRequest, int i) {
        logd("requestNetwork request=" + networkRequest + ", priority=" + i);
        this.mRequestInfos.put(Integer.valueOf(networkRequest.requestId), new RequestInfo(networkRequest, i));
        processRequests();
        return 1;
    }

    private void updatePhoneBaseForIndex(int i, PhoneBase phoneBase) {
        logd("updatePhoneBaseForIndex for phone index=" + i);
        phoneBase.getServiceStateTracker().registerForDataConnectionAttached(this.mRspHandler, i + EVENT_DATA_ATTACHED, null);
        phoneBase.getServiceStateTracker().registerForDataConnectionDetached(this.mRspHandler, i + EVENT_DATA_DETACHED, null);
        ConnectivityManager connectivityManager = (ConnectivityManager) this.mPhones[i].getContext().getSystemService("connectivity");
        if (this.mNetworkFactoryMessenger != null) {
            logd("unregister TelephonyNetworkFactory for phone index=" + i);
            connectivityManager.unregisterNetworkFactory(this.mNetworkFactoryMessenger[i]);
            this.mNetworkFactoryMessenger[i] = null;
            this.mNetworkFactory[i] = null;
            this.mNetworkFilter[i] = null;
        }
        this.mNetworkFilter[i] = new NetworkCapabilities();
        this.mNetworkFilter[i].addTransportType(0);
        this.mNetworkFilter[i].addCapability(0);
        this.mNetworkFilter[i].addCapability(1);
        this.mNetworkFilter[i].addCapability(2);
        this.mNetworkFilter[i].addCapability(3);
        this.mNetworkFilter[i].addCapability(4);
        this.mNetworkFilter[i].addCapability(5);
        this.mNetworkFilter[i].addCapability(7);
        this.mNetworkFilter[i].addCapability(8);
        this.mNetworkFilter[i].addCapability(9);
        this.mNetworkFilter[i].addCapability(10);
        this.mNetworkFilter[i].addCapability(13);
        this.mNetworkFilter[i].addCapability(12);
        this.mNetworkFactory[i] = new TelephonyNetworkFactory(getLooper(), this.mPhones[i].getContext(), "TelephonyNetworkFactory", phoneBase, this.mNetworkFilter[i]);
        this.mNetworkFactory[i].setScoreFilter(50);
        this.mNetworkFactoryMessenger[i] = new Messenger(this.mNetworkFactory[i]);
        connectivityManager.registerNetworkFactory(this.mNetworkFactoryMessenger[i], "Telephony");
    }

    private void updateSubIdAndCapability() {
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mPhoneNum) {
                ((TelephonyNetworkFactory) this.mNetworkFactory[i2]).updateNetworkCapability();
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    public void dispose() {
        logd("DctController.dispose");
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mPhoneNum) {
                ((ConnectivityManager) this.mPhones[i2].getContext().getSystemService("connectivity")).unregisterNetworkFactory(this.mNetworkFactoryMessenger[i2]);
                this.mNetworkFactoryMessenger[i2] = null;
                i = i2 + 1;
            } else {
                releaseAllNetworkRequests();
                this.mContext.unregisterReceiver(this.defaultDdsBroadcastReceiver);
                this.mSubMgr.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
                this.mContext.getContentResolver().unregisterContentObserver(this.mObserver);
                return;
            }
        }
    }

    public void doPsAttach(NetworkRequest networkRequest) {
        Rlog.d(LOG_TAG, "doPsAttach for :" + networkRequest);
        int phoneId = this.mSubController.getPhoneId(this.mSubController.getSubIdFromNetworkRequest(networkRequest));
        DcTrackerBase dcTrackerBase = ((PhoneBase) this.mPhones[phoneId].getActivePhone()).mDcTracker;
        Message obtain = Message.obtain(this, 5, new SwitchInfo(new Integer(phoneId).intValue(), networkRequest, false, true));
        informDefaultDdsToPropServ(getDataConnectionFromSetting());
        dcTrackerBase.setDataAllowed(true, obtain);
    }

    public void doPsDetach() {
        int currentDds = this.mSubController.getCurrentDds();
        if (currentDds == this.mSubController.getDefaultDataSubId()) {
            Rlog.d(LOG_TAG, "PS DETACH on DDS sub is not allowed.");
            return;
        }
        Rlog.d(LOG_TAG, "doPsDetach for sub:" + currentDds);
        ((PhoneBase) this.mPhones[this.mSubController.getPhoneId(this.mSubController.getCurrentDds())].getActivePhone()).mDcTracker.setDataAllowed(false, null);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("DctController:");
        try {
            for (DcSwitchStateMachine dump : this.mDcSwitchStateMachine) {
                dump.dump(fileDescriptor, printWriter, strArr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            for (Entry entry : this.mRequestInfos.entrySet()) {
                printWriter.println("mRequestInfos[" + entry.getKey() + "]=" + entry.getValue());
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        printWriter.flush();
    }

    /* Access modifiers changed, original: 0000 */
    public void executeAllRequests(int i) {
        logd("executeAllRequests, phone:" + i);
        sendMessage(obtainMessage(EVENT_EXECUTE_ALL_REQUESTS, i, 0));
    }

    /* Access modifiers changed, original: 0000 */
    public void executeRequest(RequestInfo requestInfo) {
        logd("executeRequest, request= " + requestInfo);
        sendMessage(obtainMessage(EVENT_EXECUTE_REQUEST, requestInfo));
    }

    public void handleMessage(Message message) {
        boolean z;
        SwitchInfo switchInfo;
        Rlog.d(LOG_TAG, "handleMessage msg=" + message);
        AsyncResult asyncResult;
        SwitchInfo switchInfo2;
        switch (message.what) {
            case 1:
                z = false;
                break;
            case 3:
                Rlog.d(LOG_TAG, "EVENT_DELAYED_RETRY");
                switchInfo = (SwitchInfo) message.obj;
                Rlog.d(LOG_TAG, " Retry, switchInfo = " + switchInfo);
                Integer valueOf = Integer.valueOf(switchInfo.mPhoneId);
                this.mSubController.getSubId(valueOf.intValue());
                ((PhoneBase) this.mPhones[valueOf.intValue()].getActivePhone()).mDcTracker.setDataAllowed(true, Message.obtain(this, 5, switchInfo));
                return;
            case 4:
                z = true;
                break;
            case 5:
                Throwable runtimeException;
                asyncResult = (AsyncResult) message.obj;
                switchInfo2 = (SwitchInfo) asyncResult.userObj;
                int[] subId = this.mSubController.getSubId(Integer.valueOf(switchInfo2.mPhoneId).intValue());
                Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOW_TRUE_DONE  subId :" + subId[0] + ", switchInfo = " + switchInfo2);
                if (asyncResult.exception != null) {
                    Rlog.d(LOG_TAG, "Failed, switchInfo = " + switchInfo2 + " attempt delayed retry");
                    switchInfo2.incRetryCount();
                    if (switchInfo2.isRetryPossible()) {
                        sendMessageDelayed(obtainMessage(3, switchInfo2), 10000);
                        return;
                    } else {
                        Rlog.d(LOG_TAG, "Already did max retries, notify failure");
                        runtimeException = new RuntimeException("PS ATTACH failed");
                    }
                } else {
                    Rlog.d(LOG_TAG, "PS ATTACH success = " + switchInfo2);
                    runtimeException = null;
                }
                this.mDdsSwitchSerializer.unLock();
                if (switchInfo2.mIsDefaultDataSwitchRequested) {
                    this.mNotifyDefaultDataSwitchInfo.notifyRegistrants(new AsyncResult(null, Integer.valueOf(subId[0]), runtimeException));
                    return;
                } else if (switchInfo2.mIsOnDemandPsAttachRequested) {
                    this.mNotifyOnDemandPsAttach.notifyRegistrants(new AsyncResult(null, switchInfo2.mNetworkRequest, runtimeException));
                    return;
                } else {
                    this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult(null, switchInfo2.mNetworkRequest, runtimeException));
                    return;
                }
            case 6:
                asyncResult = (AsyncResult) message.obj;
                switchInfo2 = (SwitchInfo) asyncResult.userObj;
                Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOW_FALSE_DONE  subId :" + this.mSubController.getSubId(switchInfo2.mPhoneId)[0] + ", switchInfo = " + switchInfo2);
                if (asyncResult.exception != null) {
                    Rlog.d(LOG_TAG, "PS DETACH Failed, switchInfo = " + switchInfo2);
                    RuntimeException runtimeException2 = new RuntimeException("PS DETACH failed");
                    this.mDdsSwitchSerializer.unLock();
                    this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult(null, switchInfo2.mNetworkRequest, runtimeException2));
                    return;
                }
                Rlog.d(LOG_TAG, "PS DETACH success = " + switchInfo2);
                return;
            case 100:
                onProcessRequest();
                return;
            case EVENT_EXECUTE_REQUEST /*101*/:
                onExecuteRequest((RequestInfo) message.obj);
                return;
            case EVENT_EXECUTE_ALL_REQUESTS /*102*/:
                onExecuteAllRequests(message.arg1);
                return;
            case EVENT_RELEASE_REQUEST /*103*/:
                onReleaseRequest((RequestInfo) message.obj);
                return;
            case EVENT_RELEASE_ALL_REQUESTS /*104*/:
                onReleaseAllRequests(message.arg1);
                return;
            case 69632:
                if (message.arg1 == 0) {
                    logd("HALF_CONNECTED: Connection successful with DDS switch service");
                    this.mDdsSwitchPropService = (AsyncChannel) message.obj;
                    return;
                }
                logd("HALF_CONNECTED: Connection failed with DDS switch service, err = " + message.arg1);
                return;
            case 69636:
                logd("Connection disconnected with DDS switch service");
                this.mDdsSwitchPropService = null;
                return;
            default:
                loge("Un-handled message [" + message.what + "]");
                return;
        }
        switchInfo = (SwitchInfo) ((AsyncResult) message.obj).userObj;
        Integer valueOf2 = Integer.valueOf(switchInfo.mPhoneId);
        Rlog.d(LOG_TAG, "EVENT_ALL_DATA_DISCONNECTED switchInfo :" + switchInfo + " isLegacySetDds = " + z);
        if (!z) {
            this.mPhones[this.mSubController.getPhoneId(this.mSubController.getCurrentDds())].unregisterForAllDataDisconnected(this);
        }
        Message obtain = Message.obtain(this, 5, switchInfo);
        Phone activePhone = this.mPhones[valueOf2.intValue()].getActivePhone();
        if (isOnDemandDdsSwitchInProgress) {
            informDefaultDdsToPropServ(getDataConnectionFromSetting());
            isOnDemandDdsSwitchInProgress = false;
        } else {
            informDefaultDdsToPropServ(valueOf2.intValue());
        }
        ((PhoneBase) activePhone).mDcTracker.setDataAllowed(true, obtain);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isActiveSubId(int i) {
        int[] activeSubIdList = this.mSubController.getActiveSubIdList();
        for (int i2 : activeSubIdList) {
            if (i == i2) {
                return true;
            }
        }
        return false;
    }

    public boolean isDctControllerLocked() {
        return this.mDdsSwitchSerializer.isLocked();
    }

    /* Access modifiers changed, original: 0000 */
    public void processRequests() {
        logd("processRequests");
        sendMessage(obtainMessage(100));
    }

    public void registerDdsSwitchPropService(Messenger messenger) {
        logd("Got messenger from DDS switch service, messenger = " + messenger);
        new AsyncChannel().connect(this.mContext, sDctController, messenger);
    }

    public void registerForDefaultDataSwitchInfo(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mNotifyDefaultDataSwitchInfo) {
            this.mNotifyDefaultDataSwitchInfo.add(registrant);
        }
    }

    public void registerForOnDemandDataSwitchInfo(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mNotifyOnDemandDataSwitchInfo) {
            this.mNotifyOnDemandDataSwitchInfo.add(registrant);
        }
    }

    public void registerForOnDemandPsAttach(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mNotifyOnDemandPsAttach) {
            this.mNotifyOnDemandPsAttach.add(registrant);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void releaseAllRequests(int i) {
        logd("releaseAllRequests, phone:" + i);
        sendMessage(obtainMessage(EVENT_RELEASE_ALL_REQUESTS, i, 0));
    }

    /* Access modifiers changed, original: 0000 */
    public void releaseRequest(RequestInfo requestInfo) {
        logd("releaseRequest, request= " + requestInfo);
        sendMessage(obtainMessage(EVENT_RELEASE_REQUEST, requestInfo));
    }

    public void setDefaultDataSubId(int i) {
        int phoneId = this.mSubController.getPhoneId(i);
        int currentDds = this.mSubController.getCurrentDds();
        int defaultDataSubId = this.mSubController.getDefaultDataSubId();
        SwitchInfo switchInfo = new SwitchInfo(new Integer(phoneId).intValue(), true);
        currentDds = this.mSubController.getPhoneId(currentDds);
        if (currentDds < 0 || currentDds >= this.mPhoneNum) {
            logd(" setDefaultDataSubId,  reqSubId = " + i + " currentDdsPhoneId  " + currentDds);
            this.mSubController.setDataSubId(i);
            currentDds = this.mSubController.getPhoneId(i);
            defaultDataSubId = i;
        }
        Rlog.d(LOG_TAG, "setDefaultDataSubId reqSubId :" + i + " reqPhoneId = " + phoneId);
        if (i == defaultDataSubId || phoneId == currentDds) {
            logd("setDefaultDataSubId for default DDS, skip PS detach on DDS subs");
            sendMessage(obtainMessage(4, new AsyncResult(switchInfo, null, null)));
            return;
        }
        doDetach(currentDds);
        this.mPhones[currentDds].registerForAllDataDisconnected(this, 1, switchInfo);
    }

    public void setOnDemandDataSubId(NetworkRequest networkRequest) {
        Rlog.d(LOG_TAG, "setDataAllowed for :" + networkRequest);
        this.mDdsSwitchSerializer.sendMessage(this.mDdsSwitchSerializer.obtainMessage(EVENT_START_DDS_SWITCH, networkRequest));
    }

    /* Access modifiers changed, original: protected */
    public void setupDataAfterDdsSwitchIfPossible() {
        int defaultDataSubId = this.mSubController.getDefaultDataSubId();
        Rlog.d(LOG_TAG, "setupDataAfterDdsSwitchIfPossible on sub = " + defaultDataSubId);
        ((PhoneBase) this.mPhones[this.mSubController.getPhoneId(defaultDataSubId)].getActivePhone()).mDcTracker.setupDataAfterDdsSwitchIfPossible();
    }

    public void updatePhoneObject(PhoneProxy phoneProxy) {
        if (phoneProxy == null) {
            loge("updatePhoneObject phone = null");
            return;
        }
        PhoneBase phoneBase = (PhoneBase) phoneProxy.getActivePhone();
        if (phoneBase == null) {
            loge("updatePhoneObject phoneBase = null");
            return;
        }
        for (int i = 0; i < this.mPhoneNum; i++) {
            if (this.mPhones[i] == phoneProxy) {
                updatePhoneBaseForIndex(i, phoneBase);
                return;
            }
        }
    }
}
