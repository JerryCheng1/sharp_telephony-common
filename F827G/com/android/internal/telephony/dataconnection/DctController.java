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
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel;
import com.android.internal.util.AsyncChannel;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private Context mContext;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private Handler[] mDcSwitchStateHandler;
    private DcSwitchStateMachine[] mDcSwitchStateMachine;
    private AsyncChannel mDdsSwitchPropService;
    private DdsSwitchSerializerHandler mDdsSwitchSerializer;
    private NetworkFactory[] mNetworkFactory;
    private Messenger[] mNetworkFactoryMessenger;
    private NetworkCapabilities[] mNetworkFilter;
    private int mPhoneNum;
    private PhoneProxy[] mPhones;
    private SubscriptionManager mSubMgr;
    private RegistrantList mNotifyDefaultDataSwitchInfo = new RegistrantList();
    private RegistrantList mNotifyOnDemandDataSwitchInfo = new RegistrantList();
    private RegistrantList mNotifyOnDemandPsAttach = new RegistrantList();
    private SubscriptionController mSubController = SubscriptionController.getInstance();
    private HashMap<Integer, DcSwitchAsyncChannel.RequestInfo> mRequestInfos = new HashMap<>();
    private boolean mIsDdsSwitchCompleted = true;
    private final int MAX_RETRY_FOR_ATTACH = 6;
    private final int ATTACH_RETRY_DELAY = 10000;
    private BroadcastReceiver defaultDdsBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.dataconnection.DctController.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            DctController.logd("got ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED, new DDS = " + intent.getIntExtra("subscription", -1));
            DctController.this.updateSubIdAndCapability();
        }
    };
    private SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangedListener = new SubscriptionManager.OnSubscriptionsChangedListener() { // from class: com.android.internal.telephony.dataconnection.DctController.2
        @Override // android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
        public void onSubscriptionsChanged() {
            DctController.this.onSubInfoReady();
        }
    };
    private ContentObserver mObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.dataconnection.DctController.3
        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            DctController.logd("Settings change");
            DctController.this.onSettingsChange();
        }
    };
    private Handler mRspHandler = new Handler() { // from class: com.android.internal.telephony.dataconnection.DctController.4
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what >= DctController.EVENT_DATA_DETACHED) {
                DctController.logd("EVENT_PHONE" + ((msg.what - 600) + 1) + "_DATA_DETACH.");
                DctController.this.mDcSwitchAsyncChannel[msg.what - 600].notifyDataDetached();
            } else if (msg.what >= DctController.EVENT_DATA_ATTACHED) {
                DctController.logd("EVENT_PHONE" + ((msg.what - 500) + 1) + "_DATA_ATTACH.");
                DctController.this.mDcSwitchAsyncChannel[msg.what - 500].notifyDataAttached();
            }
        }
    };

    public void updateSubIdAndCapability() {
        for (int i = 0; i < this.mPhoneNum; i++) {
            this.mNetworkFactory[i].updateNetworkCapability();
        }
    }

    private void releaseAllNetworkRequests() {
        for (int i = 0; i < this.mPhoneNum; i++) {
            this.mNetworkFactory[i].releaseAllNetworkRequests();
        }
    }

    boolean isActiveSubId(int subId) {
        for (int i : this.mSubController.getActiveSubIdList()) {
            if (subId == i) {
                return true;
            }
        }
        return false;
    }

    public void updatePhoneObject(PhoneProxy phone) {
        if (phone == null) {
            loge("updatePhoneObject phone = null");
            return;
        }
        PhoneBase phoneBase = (PhoneBase) phone.getActivePhone();
        if (phoneBase == null) {
            loge("updatePhoneObject phoneBase = null");
            return;
        }
        for (int i = 0; i < this.mPhoneNum; i++) {
            if (this.mPhones[i] == phone) {
                updatePhoneBaseForIndex(i, phoneBase);
                return;
            }
        }
    }

    private void updatePhoneBaseForIndex(int index, PhoneBase phoneBase) {
        logd("updatePhoneBaseForIndex for phone index=" + index);
        phoneBase.getServiceStateTracker().registerForDataConnectionAttached(this.mRspHandler, index + EVENT_DATA_ATTACHED, null);
        phoneBase.getServiceStateTracker().registerForDataConnectionDetached(this.mRspHandler, index + EVENT_DATA_DETACHED, null);
        ConnectivityManager cm = (ConnectivityManager) this.mPhones[index].getContext().getSystemService("connectivity");
        if (this.mNetworkFactoryMessenger != null) {
            logd("unregister TelephonyNetworkFactory for phone index=" + index);
            cm.unregisterNetworkFactory(this.mNetworkFactoryMessenger[index]);
            this.mNetworkFactoryMessenger[index] = null;
            this.mNetworkFactory[index] = null;
            this.mNetworkFilter[index] = null;
        }
        this.mNetworkFilter[index] = new NetworkCapabilities();
        this.mNetworkFilter[index].addTransportType(0);
        this.mNetworkFilter[index].addCapability(0);
        this.mNetworkFilter[index].addCapability(1);
        this.mNetworkFilter[index].addCapability(2);
        this.mNetworkFilter[index].addCapability(3);
        this.mNetworkFilter[index].addCapability(4);
        this.mNetworkFilter[index].addCapability(5);
        this.mNetworkFilter[index].addCapability(7);
        this.mNetworkFilter[index].addCapability(8);
        this.mNetworkFilter[index].addCapability(9);
        this.mNetworkFilter[index].addCapability(10);
        this.mNetworkFilter[index].addCapability(13);
        this.mNetworkFilter[index].addCapability(12);
        this.mNetworkFactory[index] = new TelephonyNetworkFactory(getLooper(), this.mPhones[index].getContext(), "TelephonyNetworkFactory", phoneBase, this.mNetworkFilter[index]);
        this.mNetworkFactory[index].setScoreFilter(50);
        this.mNetworkFactoryMessenger[index] = new Messenger(this.mNetworkFactory[index]);
        cm.registerNetworkFactory(this.mNetworkFactoryMessenger[index], "Telephony");
    }

    public static DctController getInstance() {
        if (sDctController != null) {
            return sDctController;
        }
        throw new RuntimeException("DctController.getInstance can't be called before makeDCTController()");
    }

    public static DctController makeDctController(PhoneProxy[] phones, Looper looper) {
        if (sDctController == null) {
            logd("makeDctController: new DctController phones.length=" + phones.length);
            sDctController = new DctController(phones, looper);
            DdsScheduler.init();
        }
        logd("makeDctController: X sDctController=" + sDctController);
        return sDctController;
    }

    private DctController(PhoneProxy[] phones, Looper looper) {
        super(looper);
        logd("DctController(): phones.length=" + phones.length);
        if (phones != null && phones.length != 0) {
            this.mPhoneNum = phones.length;
            this.mPhones = phones;
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
            HandlerThread t = new HandlerThread("DdsSwitchSerializer");
            t.start();
            this.mDdsSwitchSerializer = new DdsSwitchSerializerHandler(t.getLooper());
            this.mContext.registerReceiver(this.defaultDdsBroadcastReceiver, new IntentFilter("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED"));
            this.mSubMgr = SubscriptionManager.from(this.mContext);
            this.mSubMgr.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
            this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("multi_sim_data_call"), false, this.mObserver);
        } else if (phones == null) {
            loge("DctController(phones): UNEXPECTED phones=null, ignore");
        } else {
            loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
        }
    }

    public void dispose() {
        logd("DctController.dispose");
        for (int i = 0; i < this.mPhoneNum; i++) {
            ((ConnectivityManager) this.mPhones[i].getContext().getSystemService("connectivity")).unregisterNetworkFactory(this.mNetworkFactoryMessenger[i]);
            this.mNetworkFactoryMessenger[i] = null;
        }
        releaseAllNetworkRequests();
        this.mContext.unregisterReceiver(this.defaultDdsBroadcastReceiver);
        this.mSubMgr.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mContext.getContentResolver().unregisterContentObserver(this.mObserver);
    }

    public int requestNetwork(NetworkRequest request, int priority) {
        logd("requestNetwork request=" + request + ", priority=" + priority);
        this.mRequestInfos.put(Integer.valueOf(request.requestId), new DcSwitchAsyncChannel.RequestInfo(request, priority));
        processRequests();
        return 1;
    }

    public int releaseNetwork(NetworkRequest request) {
        DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(Integer.valueOf(request.requestId));
        logd("releaseNetwork request=" + request + ", requestInfo=" + requestInfo);
        this.mRequestInfos.remove(Integer.valueOf(request.requestId));
        releaseRequest(requestInfo);
        processRequests();
        return 1;
    }

    public void processRequests() {
        logd("processRequests");
        sendMessage(obtainMessage(100));
    }

    public void executeRequest(DcSwitchAsyncChannel.RequestInfo request) {
        logd("executeRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_EXECUTE_REQUEST, request));
    }

    public void executeAllRequests(int phoneId) {
        logd("executeAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_EXECUTE_ALL_REQUESTS, phoneId, 0));
    }

    public void releaseRequest(DcSwitchAsyncChannel.RequestInfo request) {
        logd("releaseRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_RELEASE_REQUEST, request));
    }

    public void releaseAllRequests(int phoneId) {
        logd("releaseAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_RELEASE_ALL_REQUESTS, phoneId, 0));
    }

    private void onProcessRequest() {
        int phoneId = getTopPriorityRequestPhoneId();
        int activePhoneId = -1;
        int i = 0;
        while (true) {
            if (i >= this.mDcSwitchStateMachine.length) {
                break;
            } else if (!this.mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            } else {
                i++;
            }
        }
        logd("onProcessRequest phoneId=" + phoneId + ", activePhoneId=" + activePhoneId);
        if (activePhoneId == -1 || activePhoneId == phoneId) {
            for (Integer num : this.mRequestInfos.keySet()) {
                DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(num);
                if (getRequestPhoneId(requestInfo.request) == phoneId && !requestInfo.executed) {
                    this.mDcSwitchAsyncChannel[phoneId].connectSync(requestInfo);
                }
            }
            return;
        }
        this.mDcSwitchAsyncChannel[activePhoneId].disconnectAllSync();
    }

    private void onExecuteRequest(DcSwitchAsyncChannel.RequestInfo requestInfo) {
        logd("onExecuteRequest request=" + requestInfo);
        if (!requestInfo.executed) {
            requestInfo.executed = true;
            ((PhoneBase) this.mPhones[getRequestPhoneId(requestInfo.request)].getActivePhone()).mDcTracker.incApnRefCount(apnForNetworkRequest(requestInfo.request));
        }
    }

    private void onExecuteAllRequests(int phoneId) {
        logd("onExecuteAllRequests phoneId=" + phoneId);
        for (Integer num : this.mRequestInfos.keySet()) {
            DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(num);
            if (getRequestPhoneId(requestInfo.request) == phoneId) {
                onExecuteRequest(requestInfo);
            }
        }
    }

    private void onReleaseRequest(DcSwitchAsyncChannel.RequestInfo requestInfo) {
        logd("onReleaseRequest request=" + requestInfo);
        if (requestInfo != null && requestInfo.executed) {
            ((PhoneBase) this.mPhones[getRequestPhoneId(requestInfo.request)].getActivePhone()).mDcTracker.decApnRefCount(apnForNetworkRequest(requestInfo.request));
            requestInfo.executed = false;
        }
    }

    private void onReleaseAllRequests(int phoneId) {
        logd("onReleaseAllRequests phoneId=" + phoneId);
        for (Integer num : this.mRequestInfos.keySet()) {
            DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(num);
            if (getRequestPhoneId(requestInfo.request) == phoneId) {
                onReleaseRequest(requestInfo);
            }
        }
    }

    public void onSettingsChange() {
        int dataSubId = this.mSubController.getDefaultDataSubId();
        int activePhoneId = -1;
        int i = 0;
        while (true) {
            if (i >= this.mDcSwitchStateMachine.length) {
                break;
            } else if (!this.mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            } else {
                i++;
            }
        }
        int[] subIds = SubscriptionManager.getSubId(activePhoneId);
        if (subIds == null || subIds.length == 0) {
            loge("onSettingsChange, subIds null or length 0 for activePhoneId " + activePhoneId);
            return;
        }
        logd("onSettingsChange, data sub: " + dataSubId + ", active data sub: " + subIds[0]);
        if (subIds[0] != dataSubId) {
            for (Integer num : this.mRequestInfos.keySet()) {
                DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(num);
                String specifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
                if (specifier == null || specifier.equals("")) {
                    if (requestInfo.executed) {
                        String apn = apnForNetworkRequest(requestInfo.request);
                        logd("[setDataSubId] activePhoneId:" + activePhoneId + ", subId =" + dataSubId);
                        ((PhoneBase) this.mPhones[activePhoneId].getActivePhone()).mDcTracker.decApnRefCount(apn);
                        requestInfo.executed = false;
                    }
                }
            }
        }
        for (int i2 = 0; i2 < this.mPhoneNum; i2++) {
            this.mNetworkFactory[i2].evalPendingRequest();
        }
        processRequests();
    }

    private int getTopPriorityRequestPhoneId() {
        int phoneId;
        DcSwitchAsyncChannel.RequestInfo retRequestInfo = null;
        int priority = -1;
        for (int i = 0; i < this.mPhoneNum; i++) {
            for (Integer num : this.mRequestInfos.keySet()) {
                DcSwitchAsyncChannel.RequestInfo requestInfo = this.mRequestInfos.get(num);
                logd("selectExecPhone requestInfo = " + requestInfo);
                if (getRequestPhoneId(requestInfo.request) == i && priority < requestInfo.priority) {
                    priority = requestInfo.priority;
                    retRequestInfo = requestInfo;
                }
            }
        }
        if (retRequestInfo != null) {
            phoneId = getRequestPhoneId(retRequestInfo.request);
        } else {
            phoneId = this.mSubController.getPhoneId(this.mSubController.getDefaultDataSubId());
            logd("getTopPriorityRequestPhoneId: RequestInfo list is empty, use Dds sub phone id");
        }
        logd("getTopPriorityRequestPhoneId = " + phoneId + ", priority = " + priority);
        return phoneId;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SwitchInfo {
        public boolean mIsDefaultDataSwitchRequested;
        public boolean mIsOnDemandPsAttachRequested;
        public NetworkRequest mNetworkRequest;
        public int mPhoneId;
        private int mRetryCount;

        public SwitchInfo(int phoneId, NetworkRequest n, boolean flag, boolean isAttachReq) {
            DctController.this = r2;
            this.mRetryCount = 0;
            this.mPhoneId = phoneId;
            this.mNetworkRequest = n;
            this.mIsDefaultDataSwitchRequested = flag;
            this.mIsOnDemandPsAttachRequested = isAttachReq;
        }

        public SwitchInfo(int phoneId, boolean flag) {
            DctController.this = r2;
            this.mRetryCount = 0;
            this.mPhoneId = phoneId;
            this.mNetworkRequest = null;
            this.mIsDefaultDataSwitchRequested = flag;
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

    private void doDetach(int phoneId) {
        Phone phone = this.mPhones[phoneId].getActivePhone();
        DcTrackerBase dcTracker = ((PhoneBase) phone).mDcTracker;
        dcTracker.setDataAllowed(false, null);
        if (phone.getPhoneType() == 2) {
            dcTracker.cleanUpAllConnections("DDS switch");
        }
    }

    public void setDefaultDataSubId(int reqSubId) {
        int reqPhoneId = this.mSubController.getPhoneId(reqSubId);
        int currentDds = this.mSubController.getCurrentDds();
        int defaultDds = this.mSubController.getDefaultDataSubId();
        SwitchInfo s = new SwitchInfo(new Integer(reqPhoneId).intValue(), true);
        int currentDdsPhoneId = this.mSubController.getPhoneId(currentDds);
        if (currentDdsPhoneId < 0 || currentDdsPhoneId >= this.mPhoneNum) {
            logd(" setDefaultDataSubId,  reqSubId = " + reqSubId + " currentDdsPhoneId  " + currentDdsPhoneId);
            this.mSubController.setDataSubId(reqSubId);
            defaultDds = reqSubId;
            currentDdsPhoneId = this.mSubController.getPhoneId(defaultDds);
        }
        Rlog.d(LOG_TAG, "setDefaultDataSubId reqSubId :" + reqSubId + " reqPhoneId = " + reqPhoneId);
        if (reqSubId == defaultDds || reqPhoneId == currentDdsPhoneId) {
            logd("setDefaultDataSubId for default DDS, skip PS detach on DDS subs");
            sendMessage(obtainMessage(4, new AsyncResult(s, (Object) null, (Throwable) null)));
            return;
        }
        doDetach(currentDdsPhoneId);
        this.mPhones[currentDdsPhoneId].registerForAllDataDisconnected(this, 1, s);
    }

    private void informDefaultDdsToPropServ(int defDdsPhoneId) {
        if (this.mDdsSwitchPropService != null) {
            logd("Inform OemHookDDS service of current DDS = " + defDdsPhoneId);
            this.mDdsSwitchPropService.sendMessageSynchronously(1, defDdsPhoneId, this.mPhoneNum);
            logd("OemHookDDS service finished");
            return;
        }
        logd("OemHookDds service not ready yet");
    }

    public void doPsAttach(NetworkRequest n) {
        Rlog.d(LOG_TAG, "doPsAttach for :" + n);
        int phoneId = this.mSubController.getPhoneId(this.mSubController.getSubIdFromNetworkRequest(n));
        DcTrackerBase dcTracker = ((PhoneBase) this.mPhones[phoneId].getActivePhone()).mDcTracker;
        Message psAttachDone = Message.obtain(this, 5, new SwitchInfo(new Integer(phoneId).intValue(), n, false, true));
        informDefaultDdsToPropServ(getDataConnectionFromSetting());
        dcTracker.setDataAllowed(true, psAttachDone);
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

    public void setupDataAfterDdsSwitchIfPossible() {
        int defaultDds = this.mSubController.getDefaultDataSubId();
        Rlog.d(LOG_TAG, "setupDataAfterDdsSwitchIfPossible on sub = " + defaultDds);
        ((PhoneBase) this.mPhones[this.mSubController.getPhoneId(defaultDds)].getActivePhone()).mDcTracker.setupDataAfterDdsSwitchIfPossible();
    }

    public void setOnDemandDataSubId(NetworkRequest n) {
        Rlog.d(LOG_TAG, "setDataAllowed for :" + n);
        this.mDdsSwitchSerializer.sendMessage(this.mDdsSwitchSerializer.obtainMessage(EVENT_START_DDS_SWITCH, n));
    }

    public void registerForDefaultDataSwitchInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mNotifyDefaultDataSwitchInfo) {
            this.mNotifyDefaultDataSwitchInfo.add(r);
        }
    }

    public void registerForOnDemandDataSwitchInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mNotifyOnDemandDataSwitchInfo) {
            this.mNotifyOnDemandDataSwitchInfo.add(r);
        }
    }

    public void registerForOnDemandPsAttach(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mNotifyOnDemandPsAttach) {
            this.mNotifyOnDemandPsAttach.add(r);
        }
    }

    public void registerDdsSwitchPropService(Messenger messenger) {
        logd("Got messenger from DDS switch service, messenger = " + messenger);
        new AsyncChannel().connect(this.mContext, sDctController, messenger);
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        boolean isLegacySetDds = false;
        Rlog.d(LOG_TAG, "handleMessage msg=" + msg);
        switch (msg.what) {
            case 1:
                break;
            case 3:
                Rlog.d(LOG_TAG, "EVENT_DELAYED_RETRY");
                SwitchInfo s = (SwitchInfo) msg.obj;
                Rlog.d(LOG_TAG, " Retry, switchInfo = " + s);
                Integer phoneId = Integer.valueOf(s.mPhoneId);
                this.mSubController.getSubId(phoneId.intValue());
                ((PhoneBase) this.mPhones[phoneId.intValue()].getActivePhone()).mDcTracker.setDataAllowed(true, Message.obtain(this, 5, s));
                return;
            case 4:
                isLegacySetDds = true;
                break;
            case 5:
                AsyncResult ar = (AsyncResult) msg.obj;
                SwitchInfo s2 = (SwitchInfo) ar.userObj;
                Exception errorEx = null;
                int[] subId = this.mSubController.getSubId(Integer.valueOf(s2.mPhoneId).intValue());
                Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOW_TRUE_DONE  subId :" + subId[0] + ", switchInfo = " + s2);
                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "Failed, switchInfo = " + s2 + " attempt delayed retry");
                    s2.incRetryCount();
                    if (s2.isRetryPossible()) {
                        sendMessageDelayed(obtainMessage(3, s2), 10000L);
                        return;
                    } else {
                        Rlog.d(LOG_TAG, "Already did max retries, notify failure");
                        errorEx = new RuntimeException("PS ATTACH failed");
                    }
                } else {
                    Rlog.d(LOG_TAG, "PS ATTACH success = " + s2);
                }
                this.mDdsSwitchSerializer.unLock();
                if (s2.mIsDefaultDataSwitchRequested) {
                    this.mNotifyDefaultDataSwitchInfo.notifyRegistrants(new AsyncResult((Object) null, Integer.valueOf(subId[0]), errorEx));
                    return;
                } else if (s2.mIsOnDemandPsAttachRequested) {
                    this.mNotifyOnDemandPsAttach.notifyRegistrants(new AsyncResult((Object) null, s2.mNetworkRequest, errorEx));
                    return;
                } else {
                    this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult((Object) null, s2.mNetworkRequest, errorEx));
                    return;
                }
            case 6:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                SwitchInfo s3 = (SwitchInfo) ar2.userObj;
                Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOW_FALSE_DONE  subId :" + this.mSubController.getSubId(s3.mPhoneId)[0] + ", switchInfo = " + s3);
                if (ar2.exception != null) {
                    Rlog.d(LOG_TAG, "PS DETACH Failed, switchInfo = " + s3);
                    Exception errorEx2 = new RuntimeException("PS DETACH failed");
                    this.mDdsSwitchSerializer.unLock();
                    this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult((Object) null, s3.mNetworkRequest, errorEx2));
                    return;
                }
                Rlog.d(LOG_TAG, "PS DETACH success = " + s3);
                return;
            case 100:
                onProcessRequest();
                return;
            case EVENT_EXECUTE_REQUEST /* 101 */:
                onExecuteRequest((DcSwitchAsyncChannel.RequestInfo) msg.obj);
                return;
            case EVENT_EXECUTE_ALL_REQUESTS /* 102 */:
                onExecuteAllRequests(msg.arg1);
                return;
            case EVENT_RELEASE_REQUEST /* 103 */:
                onReleaseRequest((DcSwitchAsyncChannel.RequestInfo) msg.obj);
                return;
            case EVENT_RELEASE_ALL_REQUESTS /* 104 */:
                onReleaseAllRequests(msg.arg1);
                return;
            case 69632:
                if (msg.arg1 == 0) {
                    logd("HALF_CONNECTED: Connection successful with DDS switch service");
                    this.mDdsSwitchPropService = (AsyncChannel) msg.obj;
                    return;
                }
                logd("HALF_CONNECTED: Connection failed with DDS switch service, err = " + msg.arg1);
                return;
            case 69636:
                logd("Connection disconnected with DDS switch service");
                this.mDdsSwitchPropService = null;
                return;
            default:
                loge("Un-handled message [" + msg.what + "]");
                return;
        }
        SwitchInfo s4 = (SwitchInfo) ((AsyncResult) msg.obj).userObj;
        Integer phoneId2 = Integer.valueOf(s4.mPhoneId);
        Rlog.d(LOG_TAG, "EVENT_ALL_DATA_DISCONNECTED switchInfo :" + s4 + " isLegacySetDds = " + isLegacySetDds);
        if (!isLegacySetDds) {
            this.mPhones[this.mSubController.getPhoneId(this.mSubController.getCurrentDds())].unregisterForAllDataDisconnected(this);
        }
        Message allowedDataDone = Message.obtain(this, 5, s4);
        Phone phone = this.mPhones[phoneId2.intValue()].getActivePhone();
        if (!isOnDemandDdsSwitchInProgress) {
            informDefaultDdsToPropServ(phoneId2.intValue());
        } else {
            informDefaultDdsToPropServ(getDataConnectionFromSetting());
            isOnDemandDdsSwitchInProgress = false;
        }
        ((PhoneBase) phone).mDcTracker.setDataAllowed(true, allowedDataDone);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DdsSwitchSerializerHandler extends Handler {
        static final String TAG = "DdsSwitchSerializer";

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public DdsSwitchSerializerHandler(Looper looper) {
            super(looper);
            DctController.this = r1;
        }

        public void unLock() {
            Rlog.d(TAG, "unLock the DdsSwitchSerializer");
            synchronized (this) {
                DctController.this.mIsDdsSwitchCompleted = true;
                Rlog.d(TAG, "unLocked the DdsSwitchSerializer");
                notifyAll();
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

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DctController.EVENT_START_DDS_SWITCH /* 105 */:
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
                        NetworkRequest n = (NetworkRequest) msg.obj;
                        Rlog.d(TAG, "start the DDS switch for req " + n);
                        int subId = DctController.this.mSubController.getSubIdFromNetworkRequest(n);
                        if (subId == DctController.this.mSubController.getCurrentDds()) {
                            Rlog.d(TAG, "No change in DDS, respond back");
                            DctController.this.mIsDdsSwitchCompleted = true;
                            DctController.this.mNotifyOnDemandDataSwitchInfo.notifyRegistrants(new AsyncResult((Object) null, n, (Throwable) null));
                            return;
                        }
                        int phoneId = DctController.this.mSubController.getPhoneId(subId);
                        int prefPhoneId = DctController.this.mSubController.getPhoneId(DctController.this.mSubController.getCurrentDds());
                        Phone phone = DctController.this.mPhones[prefPhoneId].getActivePhone();
                        DcTrackerBase dcTracker = ((PhoneBase) phone).mDcTracker;
                        dcTracker.setDataAllowed(false, Message.obtain(DctController.this, 6, new SwitchInfo(new Integer(prefPhoneId).intValue(), n, false, false)));
                        if (phone.getPhoneType() == 2) {
                            dcTracker.cleanUpAllConnections("Ondemand DDS switch");
                        }
                        DctController.this.mPhones[prefPhoneId].registerForAllDataDisconnected(DctController.sDctController, 1, new SwitchInfo(new Integer(phoneId).intValue(), n, false, false));
                        boolean unused = DctController.isOnDemandDdsSwitchInProgress = true;
                        return;
                    } catch (Exception e) {
                        Rlog.d(TAG, "Exception while serializing the DDS switch request , e=" + e);
                        return;
                    }
                default:
                    return;
            }
        }
    }

    public boolean isDctControllerLocked() {
        return this.mDdsSwitchSerializer.isLocked();
    }

    public void onSubInfoReady() {
        logd("onSubInfoReady mPhoneNum=" + this.mPhoneNum);
        for (int i = 0; i < this.mPhoneNum; i++) {
            int subId = this.mPhones[i].getSubId();
            logd("onSubInfoReady handle pending requests subId=" + subId);
            this.mNetworkFilter[i].setNetworkSpecifier(String.valueOf(subId));
            this.mNetworkFactory[i].registerOnDemandDdsCallback();
            this.mNetworkFactory[i].evalPendingRequest();
        }
        processRequests();
    }

    public String apnForNetworkRequest(NetworkRequest nr) {
        NetworkCapabilities nc = nr.networkCapabilities;
        if (nc.getTransportTypes().length > 0 && !nc.hasTransport(0)) {
            return null;
        }
        int type = -1;
        String name = null;
        boolean error = false;
        if (nc.hasCapability(12)) {
            if (0 != 0) {
                error = true;
            }
            name = "default";
            type = 0;
        }
        if (nc.hasCapability(0)) {
            if (name != null) {
                error = true;
            }
            name = "mms";
            type = 2;
        }
        if (nc.hasCapability(1)) {
            if (name != null) {
                error = true;
            }
            name = "supl";
            type = 3;
        }
        if (nc.hasCapability(2)) {
            if (name != null) {
                error = true;
            }
            name = "dun";
            type = 4;
        }
        if (nc.hasCapability(3)) {
            if (name != null) {
                error = true;
            }
            name = "fota";
            type = 10;
        }
        if (nc.hasCapability(4)) {
            if (name != null) {
                error = true;
            }
            name = "ims";
            type = 11;
        }
        if (nc.hasCapability(5)) {
            if (name != null) {
                error = true;
            }
            name = "cbs";
            type = 12;
        }
        if (nc.hasCapability(7)) {
            if (name != null) {
                error = true;
            }
            name = "ia";
            type = 14;
        }
        if (nc.hasCapability(8)) {
            if (name != null) {
                error = true;
            }
            name = null;
            loge("RCS APN type not yet supported");
        }
        if (nc.hasCapability(9)) {
            if (name != null) {
                error = true;
            }
            name = null;
            loge("XCAP APN type not yet supported");
        }
        if (nc.hasCapability(10)) {
            if (name != null) {
                error = true;
            }
            name = null;
            loge("EIMS APN type not yet supported");
        }
        if (error) {
            loge("Multiple apn types specified in request - result is unspecified!");
        }
        if (type != -1 && name != null) {
            return name;
        }
        loge("Unsupported NetworkRequest in Telephony: nr=" + nr);
        return null;
    }

    public int getRequestPhoneId(NetworkRequest networkRequest) {
        int subId;
        String specifier = networkRequest.networkCapabilities.getNetworkSpecifier();
        if (specifier == null || specifier.equals("")) {
            subId = this.mSubController.getDefaultDataSubId();
        } else {
            subId = Integer.parseInt(specifier);
        }
        int phoneId = this.mSubController.getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            phoneId = 0;
            if (!SubscriptionManager.isValidPhoneId(0)) {
                throw new RuntimeException("Should not happen, no valid phoneId");
            }
        }
        return phoneId;
    }

    private int getDataConnectionFromSetting() {
        return SubscriptionManager.getPhoneId(this.mSubController.getDefaultDataSubId());
    }

    public static void logd(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private static void loge(String s) {
        Rlog.e(LOG_TAG, s);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class TelephonyNetworkFactory extends NetworkFactory {
        private NetworkCapabilities mNetworkCapabilities;
        private Phone mPhone;
        private SparseArray<NetworkRequest> mDdsRequests = new SparseArray<>();
        private final SparseArray<NetworkRequest> mPendingReq = new SparseArray<>();

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public TelephonyNetworkFactory(Looper l, Context c, String TAG, Phone phone, NetworkCapabilities nc) {
            super(l, c, TAG, nc);
            DctController.this = r3;
            this.mPhone = phone;
            this.mNetworkCapabilities = nc;
            log("NetworkCapabilities: " + nc);
        }

        public void processPendingNetworkRequests(NetworkRequest n) {
            for (int i = 0; i < this.mDdsRequests.size(); i++) {
                NetworkRequest nr = this.mDdsRequests.valueAt(i);
                if (nr.equals(n)) {
                    log("Found pending request in ddsRequest list = " + nr);
                    String apn = DctController.this.apnForNetworkRequest(nr);
                    DcTrackerBase dcTracker = ((PhoneBase) this.mPhone).mDcTracker;
                    if (dcTracker.isApnSupported(apn)) {
                        dcTracker.incApnRefCount(apn);
                    } else {
                        log("Unsupported APN");
                    }
                }
            }
        }

        public void registerOnDemandDdsCallback() {
            SubscriptionController.getInstance().registerForOnDemandDdsLockNotification(this.mPhone.getSubId(), new SubscriptionController.OnDemandDdsLockNotifier() { // from class: com.android.internal.telephony.dataconnection.DctController.TelephonyNetworkFactory.1
                @Override // com.android.internal.telephony.SubscriptionController.OnDemandDdsLockNotifier
                public void notifyOnDemandDdsLockGranted(NetworkRequest n) {
                    TelephonyNetworkFactory.this.log("Got the tempDds lock for the request = " + n);
                    TelephonyNetworkFactory.this.processPendingNetworkRequests(n);
                }
            });
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

        protected void needNetworkFor(NetworkRequest networkRequest, int score) {
            log("Cellular needs Network for " + networkRequest);
            int subId = this.mPhone.getSubId();
            if (!SubscriptionManager.isUsableSubIdValue(subId) || SubscriptionController.getInstance().getSubState(subId) != 1) {
                log("Sub Info has not been ready, pending request.");
                this.mPendingReq.put(networkRequest.requestId, networkRequest);
                return;
            }
            SubscriptionController subController = SubscriptionController.getInstance();
            log("subController = " + subController);
            int currentDds = subController.getDefaultDataSubId();
            int requestedSpecifier = subController.getSubIdFromNetworkRequest(networkRequest);
            log("CurrentDds = " + currentDds);
            log("mySubId = " + subId);
            log("Requested networkSpecifier = " + requestedSpecifier);
            log("my networkSpecifier = " + this.mNetworkCapabilities.getNetworkSpecifier());
            if (!DctController.this.isActiveSubId(currentDds)) {
                log("Can't handle any network request now, currentDds not ready.");
                this.mPendingReq.put(networkRequest.requestId, networkRequest);
            } else if (requestedSpecifier != subId) {
                log("requestedSpecifier is not same as mysubId. Bail out.");
            } else if (currentDds != requestedSpecifier) {
                log("This request would result in DDS switch");
                log("Requested DDS switch to subId = " + requestedSpecifier);
                this.mDdsRequests.put(networkRequest.requestId, networkRequest);
                requestOnDemandDataSubscriptionLock(networkRequest);
            } else if (isNetworkRequestForInternet(networkRequest)) {
                log("Activating internet request on subId = " + subId);
                String apn = DctController.this.apnForNetworkRequest(networkRequest);
                DcTrackerBase dcTracker = ((PhoneBase) this.mPhone).mDcTracker;
                if (dcTracker.isApnSupported(apn)) {
                    DctController.this.requestNetwork(networkRequest, dcTracker.getApnPriority(apn));
                } else {
                    log("Unsupported APN");
                }
            } else if (isValidRequest(networkRequest)) {
                this.mDdsRequests.put(networkRequest.requestId, networkRequest);
                requestOnDemandDataSubscriptionLock(networkRequest);
            } else {
                log("Bogus request req = " + networkRequest);
            }
        }

        private boolean isValidRequest(NetworkRequest n) {
            return n.networkCapabilities.getCapabilities().length > 0;
        }

        private boolean isNetworkRequestForInternet(NetworkRequest n) {
            boolean flag = n.networkCapabilities.hasCapability(12);
            log("Is the request for Internet = " + flag);
            return flag;
        }

        private void requestOnDemandDataSubscriptionLock(NetworkRequest n) {
            if (!isNetworkRequestForInternet(n)) {
                SubscriptionController subController = SubscriptionController.getInstance();
                log("requestOnDemandDataSubscriptionLock for request = " + n);
                subController.startOnDemandDataSubscriptionRequest(n);
            }
        }

        private void removeRequestFromList(SparseArray<NetworkRequest> list, NetworkRequest n) {
            NetworkRequest nr = list.get(n.requestId);
            if (nr != null) {
                log("Removing request = " + nr);
                list.remove(n.requestId);
                String apn = DctController.this.apnForNetworkRequest(nr);
                DcTrackerBase dcTracker = ((PhoneBase) this.mPhone).mDcTracker;
                if (dcTracker.isApnSupported(apn)) {
                    dcTracker.decApnRefCount(apn);
                } else {
                    log("Unsupported APN");
                }
            }
        }

        private void removeRequestIfFound(NetworkRequest n) {
            log("Release the request from dds queue, if found");
            removeRequestFromList(this.mDdsRequests, n);
            if (!isNetworkRequestForInternet(n)) {
                SubscriptionController.getInstance().stopOnDemandDataSubscriptionRequest(n);
                return;
            }
            String apn = DctController.this.apnForNetworkRequest(n);
            DcTrackerBase dcTracker = ((PhoneBase) this.mPhone).mDcTracker;
            if (dcTracker.isApnSupported(apn)) {
                dcTracker.decApnRefCount(apn);
            } else {
                log("Unsupported APN");
            }
        }

        protected void releaseNetworkFor(NetworkRequest networkRequest) {
            log("Cellular releasing Network for " + networkRequest);
            if (this.mPendingReq.get(networkRequest.requestId) != null) {
                log("Remove the request from pending list.");
                this.mPendingReq.remove(networkRequest.requestId);
            } else if (this.mDdsRequests.get(networkRequest.requestId) != null) {
                removeRequestIfFound(networkRequest);
            } else if (DctController.this.getRequestPhoneId(networkRequest) != this.mPhone.getPhoneId()) {
                log("Request not release");
            } else if (((PhoneBase) this.mPhone).mDcTracker.isApnSupported(DctController.this.apnForNetworkRequest(networkRequest))) {
                DctController.this.releaseNetwork(networkRequest);
            } else {
                log("Unsupported APN");
            }
        }

        public void releaseAllNetworkRequests() {
            log("releaseAllNetworkRequests");
            SubscriptionController subController = SubscriptionController.getInstance();
            for (int i = 0; i < this.mDdsRequests.size(); i++) {
                NetworkRequest nr = this.mDdsRequests.valueAt(i);
                if (nr != null) {
                    log("Removing request = " + nr);
                    subController.stopOnDemandDataSubscriptionRequest(nr);
                    this.mDdsRequests.remove(nr.requestId);
                }
            }
        }

        protected void log(String s) {
            Rlog.d(DctController.LOG_TAG, "[TNF " + this.mPhone.getSubId() + "]" + s);
        }

        public void evalPendingRequest() {
            log("evalPendingRequest, pending request size is " + this.mPendingReq.size());
            for (int i = 0; i < this.mPendingReq.size(); i++) {
                NetworkRequest request = this.mPendingReq.get(this.mPendingReq.keyAt(i));
                log("evalPendingRequest: request = " + request);
                this.mPendingReq.remove(request.requestId);
                needNetworkFor(request, 0);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DctController:");
        try {
            for (DcSwitchStateMachine dssm : this.mDcSwitchStateMachine) {
                dssm.dump(fd, pw, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            for (Map.Entry<Integer, DcSwitchAsyncChannel.RequestInfo> entry : this.mRequestInfos.entrySet()) {
                pw.println("mRequestInfos[" + entry.getKey() + "]=" + entry.getValue());
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
    }
}
