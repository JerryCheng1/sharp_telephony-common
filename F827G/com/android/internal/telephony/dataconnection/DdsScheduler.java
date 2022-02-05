package com.android.internal.telephony.dataconnection;

import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import com.android.internal.telephony.ModemStackController;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DdsScheduler extends StateMachine {
    static final String TAG = "DdsScheduler";
    private static DdsScheduler sDdsScheduler;
    private DctController mDctController;
    private DefaultState mDefaultState = new DefaultState();
    private DdsIdleState mDdsIdleState = new DdsIdleState();
    private DdsReservedState mDdsReservedState = new DdsReservedState();
    private PsAttachReservedState mPsAttachReservedState = new PsAttachReservedState();
    private DdsSwitchState mDdsSwitchState = new DdsSwitchState();
    private DdsAutoRevertState mDdsAutoRevertState = new DdsAutoRevertState();
    private int mCurrentDds = -1;
    private final int MODEM_DATA_CAPABILITY_UNKNOWN = -1;
    private final int MODEM_SINGLE_DATA_CAPABLE = 1;
    private final int MODEM_DUAL_DATA_CAPABLE = 2;
    private final String OVERRIDE_MODEM_DUAL_DATA_CAP_PROP = "persist.test.msim.config";
    private List<NetworkRequestInfo> mInbox = Collections.synchronizedList(new ArrayList());

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class NetworkRequestInfo {
        public boolean mAccepted = false;
        public final NetworkRequest mRequest;

        NetworkRequestInfo(NetworkRequest req) {
            DdsScheduler.this = r2;
            this.mRequest = req;
        }

        public String toString() {
            return this.mRequest + " accepted = " + this.mAccepted;
        }
    }

    private static DdsScheduler createDdsScheduler() {
        DdsScheduler ddsScheduler = new DdsScheduler();
        ddsScheduler.start();
        return ddsScheduler;
    }

    public static DdsScheduler getInstance() {
        if (sDdsScheduler == null) {
            sDdsScheduler = createDdsScheduler();
        }
        Rlog.d(TAG, "getInstance = " + sDdsScheduler);
        return sDdsScheduler;
    }

    public static void init() {
        if (sDdsScheduler == null) {
            sDdsScheduler = getInstance();
        }
        sDdsScheduler.registerCallbacks();
        Rlog.d(TAG, "init = " + sDdsScheduler);
    }

    private DdsScheduler() {
        super(TAG);
        addState(this.mDefaultState);
        addState(this.mDdsIdleState, this.mDefaultState);
        addState(this.mDdsReservedState, this.mDefaultState);
        addState(this.mDdsSwitchState, this.mDefaultState);
        addState(this.mDdsAutoRevertState, this.mDefaultState);
        addState(this.mPsAttachReservedState, this.mDefaultState);
        setInitialState(this.mDdsIdleState);
    }

    void addRequest(NetworkRequest req) {
        synchronized (this.mInbox) {
            this.mInbox.add(new NetworkRequestInfo(req));
        }
    }

    void removeRequest(NetworkRequest req) {
        synchronized (this.mInbox) {
            for (int i = 0; i < this.mInbox.size(); i++) {
                if (this.mInbox.get(i).mRequest.equals(req)) {
                    this.mInbox.remove(i);
                }
            }
        }
    }

    void markAccepted(NetworkRequest req) {
        synchronized (this.mInbox) {
            for (int i = 0; i < this.mInbox.size(); i++) {
                NetworkRequestInfo tempNrInfo = this.mInbox.get(i);
                if (tempNrInfo.mRequest.equals(req)) {
                    tempNrInfo.mAccepted = true;
                }
            }
        }
    }

    boolean isAlreadyAccepted(NetworkRequest nr) {
        boolean z = false;
        synchronized (this.mInbox) {
            int i = 0;
            while (true) {
                if (i >= this.mInbox.size()) {
                    break;
                }
                NetworkRequestInfo tempNrInfo = this.mInbox.get(i);
                if (!tempNrInfo.mRequest.equals(nr)) {
                    i++;
                } else if (tempNrInfo.mAccepted) {
                    z = true;
                }
            }
        }
        return z;
    }

    NetworkRequest getFirstWaitingRequest() {
        NetworkRequest networkRequest;
        synchronized (this.mInbox) {
            networkRequest = this.mInbox.isEmpty() ? null : this.mInbox.get(0).mRequest;
        }
        return networkRequest;
    }

    boolean acceptWaitingRequest() {
        boolean anyAccepted = false;
        synchronized (this.mInbox) {
            if (!this.mInbox.isEmpty()) {
                for (int i = 0; i < this.mInbox.size(); i++) {
                    NetworkRequest nr = this.mInbox.get(i).mRequest;
                    if (getSubIdFromNetworkRequest(nr) == getCurrentDds()) {
                        notifyRequestAccepted(nr);
                        anyAccepted = true;
                    }
                }
                return anyAccepted;
            }
            Rlog.d(TAG, "No request can be accepted for current sub");
            return false;
        }
    }

    void notifyRequestAccepted(NetworkRequest nr) {
        if (!isAlreadyAccepted(nr)) {
            markAccepted(nr);
            Rlog.d(TAG, "Accepted req = " + nr);
            SubscriptionController.getInstance().notifyOnDemandDataSubIdChanged(nr);
            return;
        }
        Rlog.d(TAG, "Already accepted/notified req = " + nr);
    }

    boolean isDdsSwitchRequired(NetworkRequest n) {
        if (getSubIdFromNetworkRequest(n) != getCurrentDds()) {
            Rlog.d(TAG, "DDS switch required for req = " + n);
            return true;
        }
        Rlog.d(TAG, "DDS switch not required for req = " + n);
        return false;
    }

    public int getCurrentDds() {
        SubscriptionController subController = SubscriptionController.getInstance();
        if (this.mCurrentDds == -1) {
            this.mCurrentDds = subController.getDefaultDataSubId();
        }
        Rlog.d(TAG, "mCurrentDds = " + this.mCurrentDds);
        return this.mCurrentDds;
    }

    public void updateCurrentDds(NetworkRequest n) {
        this.mCurrentDds = getSubIdFromNetworkRequest(n);
        Rlog.d(TAG, "mCurrentDds = " + this.mCurrentDds);
    }

    int getSubIdFromNetworkRequest(NetworkRequest n) {
        return SubscriptionController.getInstance().getSubIdFromNetworkRequest(n);
    }

    private void requestDdsSwitch(NetworkRequest n) {
        if (n != null) {
            this.mDctController.setOnDemandDataSubId(n);
        } else {
            requestPsAttach(null);
        }
    }

    public void requestPsAttach(NetworkRequest n) {
        this.mDctController.doPsAttach(n);
    }

    public void requestPsDetach() {
        this.mDctController.doPsDetach();
    }

    private int getMaxDataAllowed() {
        ModemStackController modemStackController = ModemStackController.getInstance();
        Rlog.d(TAG, "ModemStackController = " + modemStackController);
        int maxData = modemStackController.getMaxDataAllowed();
        Rlog.d(TAG, "modem value of max_data = " + maxData);
        int override = SystemProperties.getInt("persist.test.msim.config", -1);
        if (override == -1) {
            return maxData;
        }
        Rlog.d(TAG, "Overriding modem max_data_value with " + override);
        return override;
    }

    private void registerCallbacks() {
        if (this.mDctController == null) {
            Rlog.d(TAG, "registerCallbacks");
            this.mDctController = DctController.getInstance();
            this.mDctController.registerForOnDemandDataSwitchInfo(getHandler(), DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE, null);
            this.mDctController.registerForOnDemandPsAttach(getHandler(), DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE, null);
        }
    }

    void triggerSwitch(NetworkRequest n) {
        boolean multiDataSupported = false;
        if (isMultiDataSupported()) {
            multiDataSupported = true;
            Rlog.d(TAG, "Simultaneous dual-data supported");
        } else {
            Rlog.d(TAG, "Simultaneous dual-data NOT supported");
        }
        if (n == null || !multiDataSupported) {
            requestDdsSwitch(n);
        } else {
            requestPsAttach(n);
        }
    }

    boolean isMultiDataSupported() {
        if (getMaxDataAllowed() == 2) {
            return true;
        }
        return false;
    }

    boolean isAnyRequestWaiting() {
        boolean z;
        synchronized (this.mInbox) {
            z = !this.mInbox.isEmpty();
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DefaultState extends State {
        static final String TAG = "DdsScheduler[DefaultState]";

        private DefaultState() {
            DdsScheduler.this = r1;
        }

        public void enter() {
        }

        public void exit() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION, currentState = " + DdsScheduler.this.getCurrentState().getName());
                    break;
                case DdsSchedulerAc.REQ_DDS_FREE /* 540673 */:
                    Rlog.d(TAG, "REQ_DDS_FREE, currentState = " + DdsScheduler.this.getCurrentState().getName());
                    break;
                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE /* 540674 */:
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /* 540675 */:
                case DdsSchedulerAc.EVENT_MODEM_DATA_CAPABILITY_UPDATE /* 540676 */:
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    break;
                case DdsSchedulerAc.EVENT_ADD_REQUEST /* 540677 */:
                    NetworkRequest nr = (NetworkRequest) msg.obj;
                    Rlog.d(TAG, "EVENT_ADD_REQUEST = " + nr);
                    DdsScheduler.this.addRequest(nr);
                    DdsScheduler.this.sendMessage(DdsScheduler.this.obtainMessage(540672, nr));
                    break;
                case DdsSchedulerAc.EVENT_REMOVE_REQUEST /* 540678 */:
                    NetworkRequest nr2 = (NetworkRequest) msg.obj;
                    Rlog.d(TAG, "EVENT_REMOVE_REQUEST" + nr2);
                    DdsScheduler.this.removeRequest(nr2);
                    DdsScheduler.this.sendMessage(DdsScheduler.this.obtainMessage(DdsSchedulerAc.REQ_DDS_FREE, nr2));
                    break;
            }
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DdsIdleState extends State {
        static final String TAG = "DdsScheduler[DdsIdleState]";

        private DdsIdleState() {
            DdsScheduler.this = r1;
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest nr = DdsScheduler.this.getFirstWaitingRequest();
            if (nr != null) {
                Rlog.d(TAG, "Request pending = " + nr);
                if (!DdsScheduler.this.isDdsSwitchRequired(nr)) {
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                } else {
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
                }
            } else {
                Rlog.d(TAG, "Nothing to process");
            }
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    if (!DdsScheduler.this.isDdsSwitchRequired((NetworkRequest) msg.obj)) {
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                    } else {
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
                    }
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return false;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DdsReservedState extends State {
        static final String TAG = "DdsScheduler[DdsReservedState]";

        private DdsReservedState() {
            DdsScheduler.this = r1;
        }

        private void handleOtherSubRequests() {
            NetworkRequest nr = DdsScheduler.this.getFirstWaitingRequest();
            if (nr == null) {
                Rlog.d(TAG, "No more requests to accept");
                DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
            } else if (DdsScheduler.this.getSubIdFromNetworkRequest(nr) != DdsScheduler.this.getCurrentDds()) {
                Rlog.d(TAG, "Switch required for " + nr);
                DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
            } else {
                Rlog.e(TAG, "This request could not get accepted, start over. nr = " + nr);
                DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
            }
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            if (!DdsScheduler.this.acceptWaitingRequest()) {
                handleOtherSubRequests();
            }
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest n = (NetworkRequest) msg.obj;
                    if (DdsScheduler.this.getSubIdFromNetworkRequest(n) == DdsScheduler.this.getCurrentDds()) {
                        Rlog.d(TAG, "Accepting simultaneous request for current sub");
                        DdsScheduler.this.notifyRequestAccepted(n);
                        return true;
                    } else if (!DdsScheduler.this.isMultiDataSupported()) {
                        return true;
                    } else {
                        Rlog.d(TAG, "Incoming request is for on-demand subscription, n = " + n);
                        DdsScheduler.this.requestPsAttach(n);
                        return true;
                    }
                case DdsSchedulerAc.REQ_DDS_FREE /* 540673 */:
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    if (!DdsScheduler.this.acceptWaitingRequest()) {
                        Rlog.d(TAG, "Can't process next in this DDS");
                        handleOtherSubRequests();
                        return true;
                    }
                    Rlog.d(TAG, "Processing next in same DDS");
                    return true;
                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE /* 540674 */:
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return false;
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /* 540675 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n2 = (NetworkRequest) ar.result;
                    if (ar.exception == null) {
                        DdsScheduler.this.updateCurrentDds(n2);
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mPsAttachReservedState);
                        return true;
                    }
                    Rlog.d(TAG, "Switch failed, ignore the req = " + n2);
                    DdsScheduler.this.removeRequest(n2);
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class PsAttachReservedState extends State {
        static final String TAG = "DdsScheduler[PSAttachReservedState]";

        private PsAttachReservedState() {
            DdsScheduler.this = r1;
        }

        private void handleOtherSubRequests() {
            NetworkRequest nr = DdsScheduler.this.getFirstWaitingRequest();
            if (nr == null) {
                Rlog.d(TAG, "No more requests to accept");
            } else if (DdsScheduler.this.getSubIdFromNetworkRequest(nr) != DdsScheduler.this.getCurrentDds()) {
                Rlog.d(TAG, "Next request is not for current on-demand PS sub(DSDA). nr = " + nr);
                if (DdsScheduler.this.isAlreadyAccepted(nr)) {
                    Rlog.d(TAG, "Next request is already accepted on other sub in DSDA mode. nr = " + nr);
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                    return;
                }
            }
            DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            if (!DdsScheduler.this.acceptWaitingRequest()) {
                handleOtherSubRequests();
            }
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
            DdsScheduler.this.requestPsDetach();
            DdsScheduler.this.updateCurrentDds(null);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest n = (NetworkRequest) msg.obj;
                    Rlog.d(TAG, "Accepting request in dual-data mode, req = " + n);
                    DdsScheduler.this.notifyRequestAccepted(n);
                    return true;
                case DdsSchedulerAc.REQ_DDS_FREE /* 540673 */:
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    if (DdsScheduler.this.acceptWaitingRequest()) {
                        return true;
                    }
                    handleOtherSubRequests();
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return false;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DdsSwitchState extends State {
        static final String TAG = "DdsScheduler[DdsSwitchState]";

        private DdsSwitchState() {
            DdsScheduler.this = r1;
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest nr = DdsScheduler.this.getFirstWaitingRequest();
            if (nr != null) {
                DdsScheduler.this.triggerSwitch(nr);
            } else {
                Rlog.d(TAG, "Error");
            }
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE /* 540674 */:
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /* 540675 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n = (NetworkRequest) ar.result;
                    if (ar.exception == null) {
                        DdsScheduler.this.updateCurrentDds(n);
                        if (msg.what == 540675) {
                            DdsScheduler.this.transitionTo(DdsScheduler.this.mPsAttachReservedState);
                        } else {
                            DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                        }
                    } else {
                        Rlog.d(TAG, "Switch failed, move back to idle state");
                        DdsScheduler.this.removeRequest(n);
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
                    }
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return false;
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DdsAutoRevertState extends State {
        static final String TAG = "DdsScheduler[DdsAutoRevertState]";

        private DdsAutoRevertState() {
            DdsScheduler.this = r1;
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            DdsScheduler.this.triggerSwitch(null);
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /* 540675 */:
                    Rlog.d(TAG, "SET_DDS_DONE");
                    DdsScheduler.this.updateCurrentDds(null);
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsIdleState);
                    DdsScheduler.this.mDctController.setupDataAfterDdsSwitchIfPossible();
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return false;
            }
        }
    }
}
