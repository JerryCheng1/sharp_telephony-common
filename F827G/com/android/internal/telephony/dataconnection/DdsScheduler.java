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

public class DdsScheduler extends StateMachine {
    static final String TAG = "DdsScheduler";
    private static DdsScheduler sDdsScheduler;
    private final int MODEM_DATA_CAPABILITY_UNKNOWN = -1;
    private final int MODEM_DUAL_DATA_CAPABLE = 2;
    private final int MODEM_SINGLE_DATA_CAPABLE = 1;
    private final String OVERRIDE_MODEM_DUAL_DATA_CAP_PROP = "persist.test.msim.config";
    private int mCurrentDds = -1;
    private DctController mDctController;
    private DdsAutoRevertState mDdsAutoRevertState = new DdsAutoRevertState();
    private DdsIdleState mDdsIdleState = new DdsIdleState();
    private DdsReservedState mDdsReservedState = new DdsReservedState();
    private DdsSwitchState mDdsSwitchState = new DdsSwitchState();
    private DefaultState mDefaultState = new DefaultState();
    private List<NetworkRequestInfo> mInbox = Collections.synchronizedList(new ArrayList());
    private PsAttachReservedState mPsAttachReservedState = new PsAttachReservedState();

    private class DdsAutoRevertState extends State {
        static final String TAG = "DdsScheduler[DdsAutoRevertState]";

        private DdsAutoRevertState() {
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            DdsScheduler.this.triggerSwitch(null);
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /*540675*/:
                    Rlog.d(TAG, "SET_DDS_DONE");
                    DdsScheduler.this.updateCurrentDds(null);
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsIdleState);
                    DdsScheduler.this.mDctController.setupDataAfterDdsSwitchIfPossible();
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    return false;
            }
        }
    }

    private class DdsIdleState extends State {
        static final String TAG = "DdsScheduler[DdsIdleState]";

        private DdsIdleState() {
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest firstWaitingRequest = DdsScheduler.this.getFirstWaitingRequest();
            if (firstWaitingRequest != null) {
                Rlog.d(TAG, "Request pending = " + firstWaitingRequest);
                if (DdsScheduler.this.isDdsSwitchRequired(firstWaitingRequest)) {
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
                    return;
                } else {
                    DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                    return;
                }
            }
            Rlog.d(TAG, "Nothing to process");
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    if (DdsScheduler.this.isDdsSwitchRequired((NetworkRequest) message.obj)) {
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
                    } else {
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                    }
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    return false;
            }
        }
    }

    private class DdsReservedState extends State {
        static final String TAG = "DdsScheduler[DdsReservedState]";

        private DdsReservedState() {
        }

        private void handleOtherSubRequests() {
            NetworkRequest firstWaitingRequest = DdsScheduler.this.getFirstWaitingRequest();
            if (firstWaitingRequest == null) {
                Rlog.d(TAG, "No more requests to accept");
                DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
            } else if (DdsScheduler.this.getSubIdFromNetworkRequest(firstWaitingRequest) != DdsScheduler.this.getCurrentDds()) {
                Rlog.d(TAG, "Switch required for " + firstWaitingRequest);
                DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsSwitchState);
            } else {
                Rlog.e(TAG, "This request could not get accepted, start over. nr = " + firstWaitingRequest);
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

        public boolean processMessage(Message message) {
            boolean z;
            switch (message.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest networkRequest = (NetworkRequest) message.obj;
                    if (DdsScheduler.this.getSubIdFromNetworkRequest(networkRequest) != DdsScheduler.this.getCurrentDds()) {
                        if (!DdsScheduler.this.isMultiDataSupported()) {
                            z = true;
                            break;
                        }
                        Rlog.d(TAG, "Incoming request is for on-demand subscription, n = " + networkRequest);
                        DdsScheduler.this.requestPsAttach(networkRequest);
                        return true;
                    }
                    Rlog.d(TAG, "Accepting simultaneous request for current sub");
                    DdsScheduler.this.notifyRequestAccepted(networkRequest);
                    return true;
                case DdsSchedulerAc.REQ_DDS_FREE /*540673*/:
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    if (DdsScheduler.this.acceptWaitingRequest()) {
                        Rlog.d(TAG, "Processing next in same DDS");
                        return true;
                    }
                    Rlog.d(TAG, "Can't process next in this DDS");
                    handleOtherSubRequests();
                    return true;
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /*540675*/:
                    AsyncResult asyncResult = (AsyncResult) message.obj;
                    NetworkRequest networkRequest2 = (NetworkRequest) asyncResult.result;
                    if (asyncResult.exception == null) {
                        DdsScheduler.this.updateCurrentDds(networkRequest2);
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mPsAttachReservedState);
                        return true;
                    }
                    Rlog.d(TAG, "Switch failed, ignore the req = " + networkRequest2);
                    DdsScheduler.this.removeRequest(networkRequest2);
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    z = false;
                    break;
            }
            return z;
        }
    }

    private class DdsSwitchState extends State {
        static final String TAG = "DdsScheduler[DdsSwitchState]";

        private DdsSwitchState() {
        }

        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest firstWaitingRequest = DdsScheduler.this.getFirstWaitingRequest();
            if (firstWaitingRequest != null) {
                DdsScheduler.this.triggerSwitch(firstWaitingRequest);
            } else {
                Rlog.d(TAG, "Error");
            }
        }

        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE /*540674*/:
                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE /*540675*/:
                    AsyncResult asyncResult = (AsyncResult) message.obj;
                    NetworkRequest networkRequest = (NetworkRequest) asyncResult.result;
                    if (asyncResult.exception == null) {
                        DdsScheduler.this.updateCurrentDds(networkRequest);
                        if (message.what == DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE) {
                            DdsScheduler.this.transitionTo(DdsScheduler.this.mPsAttachReservedState);
                        } else {
                            DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsReservedState);
                        }
                    } else {
                        Rlog.d(TAG, "Switch failed, move back to idle state");
                        DdsScheduler.this.removeRequest(networkRequest);
                        DdsScheduler.this.transitionTo(DdsScheduler.this.mDdsAutoRevertState);
                    }
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    return false;
            }
        }
    }

    private class DefaultState extends State {
        static final String TAG = "DdsScheduler[DefaultState]";

        private DefaultState() {
        }

        public void enter() {
        }

        public void exit() {
        }

        public boolean processMessage(Message message) {
            NetworkRequest networkRequest;
            switch (message.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION, currentState = " + DdsScheduler.this.getCurrentState().getName());
                    break;
                case DdsSchedulerAc.REQ_DDS_FREE /*540673*/:
                    Rlog.d(TAG, "REQ_DDS_FREE, currentState = " + DdsScheduler.this.getCurrentState().getName());
                    break;
                case DdsSchedulerAc.EVENT_ADD_REQUEST /*540677*/:
                    networkRequest = (NetworkRequest) message.obj;
                    Rlog.d(TAG, "EVENT_ADD_REQUEST = " + networkRequest);
                    DdsScheduler.this.addRequest(networkRequest);
                    DdsScheduler.this.sendMessage(DdsScheduler.this.obtainMessage(540672, networkRequest));
                    break;
                case DdsSchedulerAc.EVENT_REMOVE_REQUEST /*540678*/:
                    networkRequest = (NetworkRequest) message.obj;
                    Rlog.d(TAG, "EVENT_REMOVE_REQUEST" + networkRequest);
                    DdsScheduler.this.removeRequest(networkRequest);
                    DdsScheduler.this.sendMessage(DdsScheduler.this.obtainMessage(DdsSchedulerAc.REQ_DDS_FREE, networkRequest));
                    break;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    break;
            }
            return true;
        }
    }

    private class NetworkRequestInfo {
        public boolean mAccepted = false;
        public final NetworkRequest mRequest;

        NetworkRequestInfo(NetworkRequest networkRequest) {
            this.mRequest = networkRequest;
        }

        public String toString() {
            return this.mRequest + " accepted = " + this.mAccepted;
        }
    }

    private class PsAttachReservedState extends State {
        static final String TAG = "DdsScheduler[PSAttachReservedState]";

        private PsAttachReservedState() {
        }

        private void handleOtherSubRequests() {
            NetworkRequest firstWaitingRequest = DdsScheduler.this.getFirstWaitingRequest();
            if (firstWaitingRequest == null) {
                Rlog.d(TAG, "No more requests to accept");
            } else if (DdsScheduler.this.getSubIdFromNetworkRequest(firstWaitingRequest) != DdsScheduler.this.getCurrentDds()) {
                Rlog.d(TAG, "Next request is not for current on-demand PS sub(DSDA). nr = " + firstWaitingRequest);
                if (DdsScheduler.this.isAlreadyAccepted(firstWaitingRequest)) {
                    Rlog.d(TAG, "Next request is already accepted on other sub in DSDA mode. nr = " + firstWaitingRequest);
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

        public boolean processMessage(Message message) {
            boolean z;
            switch (message.what) {
                case 540672:
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest networkRequest = (NetworkRequest) message.obj;
                    Rlog.d(TAG, "Accepting request in dual-data mode, req = " + networkRequest);
                    DdsScheduler.this.notifyRequestAccepted(networkRequest);
                    return true;
                case DdsSchedulerAc.REQ_DDS_FREE /*540673*/:
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    if (DdsScheduler.this.acceptWaitingRequest()) {
                        z = true;
                        break;
                    }
                    handleOtherSubRequests();
                    return true;
                default:
                    Rlog.d(TAG, "unknown msg = " + message);
                    z = false;
                    break;
            }
            return z;
        }
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

    private int getMaxDataAllowed() {
        ModemStackController instance = ModemStackController.getInstance();
        Rlog.d(TAG, "ModemStackController = " + instance);
        int maxDataAllowed = instance.getMaxDataAllowed();
        Rlog.d(TAG, "modem value of max_data = " + maxDataAllowed);
        int i = SystemProperties.getInt("persist.test.msim.config", -1);
        if (i == -1) {
            return maxDataAllowed;
        }
        Rlog.d(TAG, "Overriding modem max_data_value with " + i);
        return i;
    }

    public static void init() {
        if (sDdsScheduler == null) {
            sDdsScheduler = getInstance();
        }
        sDdsScheduler.registerCallbacks();
        Rlog.d(TAG, "init = " + sDdsScheduler);
    }

    private void registerCallbacks() {
        if (this.mDctController == null) {
            Rlog.d(TAG, "registerCallbacks");
            this.mDctController = DctController.getInstance();
            this.mDctController.registerForOnDemandDataSwitchInfo(getHandler(), DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE, null);
            this.mDctController.registerForOnDemandPsAttach(getHandler(), DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE, null);
        }
    }

    private void requestDdsSwitch(NetworkRequest networkRequest) {
        if (networkRequest != null) {
            this.mDctController.setOnDemandDataSubId(networkRequest);
        } else {
            requestPsAttach(null);
        }
    }

    private void requestPsAttach(NetworkRequest networkRequest) {
        this.mDctController.doPsAttach(networkRequest);
    }

    private void requestPsDetach() {
        this.mDctController.doPsDetach();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean acceptWaitingRequest() {
        synchronized (this.mInbox) {
            if (this.mInbox.isEmpty()) {
                Rlog.d(TAG, "No request can be accepted for current sub");
                return false;
            }
            boolean z = false;
            int i = 0;
            while (i < this.mInbox.size()) {
                boolean z2;
                NetworkRequest networkRequest = ((NetworkRequestInfo) this.mInbox.get(i)).mRequest;
                if (getSubIdFromNetworkRequest(networkRequest) == getCurrentDds()) {
                    notifyRequestAccepted(networkRequest);
                    z2 = true;
                } else {
                    z2 = z;
                }
                i++;
                z = z2;
            }
            return z;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void addRequest(NetworkRequest networkRequest) {
        synchronized (this.mInbox) {
            this.mInbox.add(new NetworkRequestInfo(networkRequest));
        }
    }

    public int getCurrentDds() {
        SubscriptionController instance = SubscriptionController.getInstance();
        if (this.mCurrentDds == -1) {
            this.mCurrentDds = instance.getDefaultDataSubId();
        }
        Rlog.d(TAG, "mCurrentDds = " + this.mCurrentDds);
        return this.mCurrentDds;
    }

    /* Access modifiers changed, original: 0000 */
    public NetworkRequest getFirstWaitingRequest() {
        synchronized (this.mInbox) {
            if (this.mInbox.isEmpty()) {
                return null;
            }
            NetworkRequest networkRequest = ((NetworkRequestInfo) this.mInbox.get(0)).mRequest;
            return networkRequest;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public int getSubIdFromNetworkRequest(NetworkRequest networkRequest) {
        return SubscriptionController.getInstance().getSubIdFromNetworkRequest(networkRequest);
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Missing block: B:21:?, code skipped:
            return r0;
     */
    public boolean isAlreadyAccepted(android.net.NetworkRequest r7) {
        /*
        r6 = this;
        r2 = 1;
        r1 = 0;
        r4 = r6.mInbox;
        monitor-enter(r4);
        r3 = r1;
    L_0x0006:
        r0 = r6.mInbox;	 Catch:{ all -> 0x0028 }
        r0 = r0.size();	 Catch:{ all -> 0x0028 }
        if (r3 >= r0) goto L_0x0025;
    L_0x000e:
        r0 = r6.mInbox;	 Catch:{ all -> 0x0028 }
        r0 = r0.get(r3);	 Catch:{ all -> 0x0028 }
        r0 = (com.android.internal.telephony.dataconnection.DdsScheduler.NetworkRequestInfo) r0;	 Catch:{ all -> 0x0028 }
        r5 = r0.mRequest;	 Catch:{ all -> 0x0028 }
        r5 = r5.equals(r7);	 Catch:{ all -> 0x0028 }
        if (r5 == 0) goto L_0x002b;
    L_0x001e:
        r0 = r0.mAccepted;	 Catch:{ all -> 0x0028 }
        if (r0 != r2) goto L_0x002f;
    L_0x0022:
        r0 = r2;
    L_0x0023:
        monitor-exit(r4);	 Catch:{ all -> 0x0028 }
    L_0x0024:
        return r0;
    L_0x0025:
        monitor-exit(r4);	 Catch:{ all -> 0x0028 }
        r0 = r1;
        goto L_0x0024;
    L_0x0028:
        r0 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x0028 }
        throw r0;
    L_0x002b:
        r0 = r3 + 1;
        r3 = r0;
        goto L_0x0006;
    L_0x002f:
        r0 = r1;
        goto L_0x0023;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DdsScheduler.isAlreadyAccepted(android.net.NetworkRequest):boolean");
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isAnyRequestWaiting() {
        boolean z;
        synchronized (this.mInbox) {
            z = !this.mInbox.isEmpty();
        }
        return z;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isDdsSwitchRequired(NetworkRequest networkRequest) {
        if (getSubIdFromNetworkRequest(networkRequest) != getCurrentDds()) {
            Rlog.d(TAG, "DDS switch required for req = " + networkRequest);
            return true;
        }
        Rlog.d(TAG, "DDS switch not required for req = " + networkRequest);
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isMultiDataSupported() {
        return getMaxDataAllowed() == 2;
    }

    /* Access modifiers changed, original: 0000 */
    public void markAccepted(NetworkRequest networkRequest) {
        synchronized (this.mInbox) {
            for (int i = 0; i < this.mInbox.size(); i++) {
                NetworkRequestInfo networkRequestInfo = (NetworkRequestInfo) this.mInbox.get(i);
                if (networkRequestInfo.mRequest.equals(networkRequest)) {
                    networkRequestInfo.mAccepted = true;
                }
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyRequestAccepted(NetworkRequest networkRequest) {
        if (isAlreadyAccepted(networkRequest)) {
            Rlog.d(TAG, "Already accepted/notified req = " + networkRequest);
            return;
        }
        markAccepted(networkRequest);
        Rlog.d(TAG, "Accepted req = " + networkRequest);
        SubscriptionController.getInstance().notifyOnDemandDataSubIdChanged(networkRequest);
    }

    /* Access modifiers changed, original: 0000 */
    public void removeRequest(NetworkRequest networkRequest) {
        synchronized (this.mInbox) {
            for (int i = 0; i < this.mInbox.size(); i++) {
                if (((NetworkRequestInfo) this.mInbox.get(i)).mRequest.equals(networkRequest)) {
                    this.mInbox.remove(i);
                }
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void triggerSwitch(NetworkRequest networkRequest) {
        Object obj = null;
        if (isMultiDataSupported()) {
            obj = 1;
            Rlog.d(TAG, "Simultaneous dual-data supported");
        } else {
            Rlog.d(TAG, "Simultaneous dual-data NOT supported");
        }
        if (networkRequest == null || obj == null) {
            requestDdsSwitch(networkRequest);
        } else {
            requestPsAttach(networkRequest);
        }
    }

    public void updateCurrentDds(NetworkRequest networkRequest) {
        this.mCurrentDds = getSubIdFromNetworkRequest(networkRequest);
        Rlog.d(TAG, "mCurrentDds = " + this.mCurrentDds);
    }
}
