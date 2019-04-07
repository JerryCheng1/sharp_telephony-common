package com.android.internal.telephony.dataconnection;

import android.net.LinkAddress;
import android.net.LinkProperties.CompareResult;
import android.net.NetworkUtils;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.Rlog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelBrand;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class DcController extends StateMachine {
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT = 1;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    static final int DATA_CONNECTION_ACTIVE_UNKNOWN = Integer.MAX_VALUE;
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private HashMap<Integer, DataConnection> mDcListActiveByCid = new HashMap();
    ArrayList<DataConnection> mDcListAll = new ArrayList();
    private DcTesterDeactivateAll mDcTesterDeactivateAll;
    private DccDefaultState mDccDefaultState = new DccDefaultState();
    private DcTrackerBase mDct;
    int mOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_UNKNOWN;
    private PhoneBase mPhone;

    private class DccDefaultState extends State {
        private DccDefaultState() {
        }

        private void onDataStateChanged(ArrayList<DataCallResponse> arrayList) {
            DataConnection dataConnection;
            DcController.this.lr("onDataStateChanged: dcsList=" + arrayList + " mDcListActiveByCid=" + DcController.this.mDcListActiveByCid);
            HashMap hashMap = new HashMap();
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                DataCallResponse dataCallResponse = (DataCallResponse) it.next();
                hashMap.put(Integer.valueOf(dataCallResponse.cid), dataCallResponse);
            }
            ArrayList arrayList2 = new ArrayList();
            for (DataConnection dataConnection2 : DcController.this.mDcListActiveByCid.values()) {
                if (hashMap.get(Integer.valueOf(dataConnection2.mCid)) == null) {
                    DcController.this.log("onDataStateChanged: add to retry dc=" + dataConnection2);
                    arrayList2.add(dataConnection2);
                }
            }
            DcController.this.log("onDataStateChanged: dcsToRetry=" + arrayList2);
            ArrayList arrayList3 = new ArrayList();
            Iterator it2 = arrayList.iterator();
            boolean z = false;
            boolean z2 = false;
            while (it2.hasNext()) {
                DataCallResponse dataCallResponse2 = (DataCallResponse) it2.next();
                dataConnection2 = (DataConnection) DcController.this.mDcListActiveByCid.get(Integer.valueOf(dataCallResponse2.cid));
                if (dataConnection2 == null) {
                    DcController.this.loge("onDataStateChanged: no associated DC yet, ignore");
                } else {
                    if (dataConnection2.mApnContexts.size() == 0) {
                        DcController.this.loge("onDataStateChanged: no connected apns, ignore");
                    } else {
                        DcController.this.log("onDataStateChanged: Found ConnId=" + dataCallResponse2.cid + " newState=" + dataCallResponse2.toString());
                        if (dataCallResponse2.active == 0) {
                            DcController.this.mDct;
                            if (DcTrackerBase.mIsCleanupRequired) {
                                arrayList3.addAll(dataConnection2.mApnContexts);
                                DcController.this.mDct;
                                DcTrackerBase.mIsCleanupRequired = false;
                            } else {
                                DcFailCause fromInt = DcFailCause.fromInt(dataCallResponse2.status);
                                DcController.this.log("onDataStateChanged: inactive failCause=" + fromInt);
                                if (fromInt.isRestartRadioFail()) {
                                    DcController.this.log("onDataStateChanged: X restart radio");
                                    DcController.this.mDct.sendRestartRadio();
                                } else if (DcController.this.mDct.isPermanentFail(fromInt)) {
                                    DcController.this.log("onDataStateChanged: inactive, add to cleanup list");
                                    arrayList3.addAll(dataConnection2.mApnContexts);
                                } else {
                                    DcController.this.log("onDataStateChanged: inactive, add to retry list");
                                    arrayList2.add(dataConnection2);
                                }
                                if (TelBrand.IS_KDDI) {
                                    ArrayList arrayList4 = new ArrayList();
                                    for (ApnContext apnContext : dataConnection2.mApnContexts) {
                                        if (apnContext.getState() == DctConstants.State.CONNECTED || apnContext.getState() == DctConstants.State.CONNECTING) {
                                            arrayList4.add(apnContext);
                                        }
                                    }
                                    it = arrayList4.iterator();
                                    while (it.hasNext()) {
                                        DcController.this.mDct.sendOemKddiFailCauseBroadcast(DcFailCause.fromInt(dataCallResponse2.status), (ApnContext) it.next());
                                    }
                                }
                            }
                        } else {
                            UpdateLinkPropertyResult updateLinkProperty = dataConnection2.updateLinkProperty(dataCallResponse2);
                            if (updateLinkProperty.oldLp.equals(updateLinkProperty.newLp)) {
                                DcController.this.log("onDataStateChanged: no change");
                            } else if (!updateLinkProperty.oldLp.isIdenticalInterfaceName(updateLinkProperty.newLp)) {
                                arrayList3.addAll(dataConnection2.mApnContexts);
                                DcController.this.log("onDataStateChanged: interface change, cleanup apns=" + dataConnection2.mApnContexts);
                            } else if (updateLinkProperty.oldLp.isIdenticalDnses(updateLinkProperty.newLp) && updateLinkProperty.oldLp.isIdenticalRoutes(updateLinkProperty.newLp) && updateLinkProperty.oldLp.isIdenticalHttpProxy(updateLinkProperty.newLp) && updateLinkProperty.oldLp.isIdenticalAddresses(updateLinkProperty.newLp)) {
                                DcController.this.log("onDataStateChanged: no changes");
                            } else {
                                CompareResult compareAddresses = updateLinkProperty.oldLp.compareAddresses(updateLinkProperty.newLp);
                                DcController.this.log("onDataStateChanged: oldLp=" + updateLinkProperty.oldLp + " newLp=" + updateLinkProperty.newLp + " car=" + compareAddresses);
                                Object obj = null;
                                for (LinkAddress linkAddress : compareAddresses.added) {
                                    for (LinkAddress address : compareAddresses.removed) {
                                        if (NetworkUtils.addressTypeMatches(address.getAddress(), linkAddress.getAddress())) {
                                            obj = 1;
                                            break;
                                        }
                                    }
                                }
                                if (obj != null) {
                                    DcController.this.log("onDataStateChanged: addr change, cleanup apns=" + dataConnection2.mApnContexts + " oldLp=" + updateLinkProperty.oldLp + " newLp=" + updateLinkProperty.newLp);
                                    arrayList3.addAll(dataConnection2.mApnContexts);
                                } else {
                                    DcController.this.log("onDataStateChanged: simple change");
                                    for (ApnContext apnContext2 : dataConnection2.mApnContexts) {
                                        DcController.this.mPhone.notifyDataConnection("linkPropertiesChanged", apnContext2.getApnType());
                                    }
                                }
                            }
                        }
                    }
                    boolean z3 = dataCallResponse2.active == 2 ? true : z;
                    if (dataCallResponse2.active == 1) {
                        z = z3;
                        z2 = true;
                    } else {
                        z = z3;
                    }
                }
            }
            int i = DcController.this.mOverallDataConnectionActiveState;
            if (!z2 || z) {
                DcController.this.log("onDataStateChanged: Data Activity updated to NONE. isAnyDataCallActive = " + z + " isAnyDataCallDormant = " + z2);
                if (z) {
                    i = 2;
                    DcController.this.mDct.sendStartNetStatPoll(Activity.NONE);
                } else {
                    i = 0;
                }
            } else {
                DcController.this.log("onDataStateChanged: Data Activity updated to DORMANT. stopNetStatePoll");
                DcController.this.mDct.sendStopNetStatPoll(Activity.DORMANT);
                i = 1;
            }
            if (DcController.this.mOverallDataConnectionActiveState != i) {
                DcController.this.mOverallDataConnectionActiveState = i;
                long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
                switch (DcController.this.mOverallDataConnectionActiveState) {
                    case 0:
                    case 1:
                        i = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
                        break;
                    case 2:
                        i = DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
                        break;
                    default:
                        i = DataConnectionRealTimeInfo.DC_POWER_STATE_UNKNOWN;
                        break;
                }
                DataConnectionRealTimeInfo dataConnectionRealTimeInfo = new DataConnectionRealTimeInfo(elapsedRealtimeNanos, i);
                DcController.this.log("onDataStateChanged: notify DcRtInfo changed dcRtInfo=" + dataConnectionRealTimeInfo);
                DcController.this.mPhone.notifyDataConnectionRealTimeInfo(dataConnectionRealTimeInfo);
            }
            DcController.this.lr("onDataStateChanged: dcsToRetry=" + arrayList2 + " apnsToCleanup=" + arrayList3);
            Iterator it3 = arrayList3.iterator();
            while (it3.hasNext()) {
                DcController.this.mDct.sendCleanUpConnection(true, (ApnContext) it3.next());
            }
            it3 = arrayList2.iterator();
            while (it3.hasNext()) {
                dataConnection2 = (DataConnection) it3.next();
                DcController.this.log("onDataStateChanged: send EVENT_LOST_CONNECTION dc.mTag=" + dataConnection2.mTag);
                dataConnection2.sendMessage(262153, dataConnection2.mTag);
            }
            DcController.this.log("onDataStateChanged: X");
        }

        public void enter() {
            DcController.this.mPhone.mCi.registerForRilConnected(DcController.this.getHandler(), 262149, null);
            DcController.this.mPhone.mCi.registerForDataNetworkStateChanged(DcController.this.getHandler(), 262151, null);
            if (Build.IS_DEBUGGABLE) {
                DcController.this.mDcTesterDeactivateAll = new DcTesterDeactivateAll(DcController.this.mPhone, DcController.this, DcController.this.getHandler());
            }
        }

        public void exit() {
            if (DcController.this.mPhone != null) {
                DcController.this.mPhone.mCi.unregisterForRilConnected(DcController.this.getHandler());
                DcController.this.mPhone.mCi.unregisterForDataNetworkStateChanged(DcController.this.getHandler());
            }
            if (DcController.this.mDcTesterDeactivateAll != null) {
                DcController.this.mDcTesterDeactivateAll.dispose();
            }
        }

        public boolean processMessage(Message message) {
            AsyncResult asyncResult;
            switch (message.what) {
                case 262149:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception != null) {
                        DcController.this.log("DccDefaultState: Unexpected exception on EVENT_RIL_CONNECTED");
                        break;
                    }
                    DcController.this.log("DccDefaultState: msg.what=EVENT_RIL_CONNECTED mRilVersion=" + asyncResult.result);
                    break;
                case 262151:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception != null) {
                        DcController.this.log("DccDefaultState: EVENT_DATA_STATE_CHANGED: exception; likely radio not available, ignore");
                        break;
                    }
                    onDataStateChanged((ArrayList) asyncResult.result);
                    break;
            }
            return true;
        }
    }

    private DcController(String str, PhoneBase phoneBase, DcTrackerBase dcTrackerBase, Handler handler) {
        super(str, handler);
        setLogRecSize(300);
        log("E ctor");
        this.mPhone = phoneBase;
        this.mDct = dcTrackerBase;
        addState(this.mDccDefaultState);
        setInitialState(this.mDccDefaultState);
        log("X ctor");
    }

    private void lr(String str) {
        logAndAddLogRec(str);
    }

    static DcController makeDcc(PhoneBase phoneBase, DcTrackerBase dcTrackerBase, Handler handler) {
        DcController dcController = new DcController("Dcc", phoneBase, dcTrackerBase, handler);
        dcController.start();
        return dcController;
    }

    /* Access modifiers changed, original: 0000 */
    public void addActiveDcByCid(DataConnection dataConnection) {
        if (dataConnection.mCid < 0) {
            log("addActiveDcByCid dc.mCid < 0 dc=" + dataConnection);
        }
        this.mDcListActiveByCid.put(Integer.valueOf(dataConnection.mCid), dataConnection);
    }

    /* Access modifiers changed, original: 0000 */
    public void addDc(DataConnection dataConnection) {
        this.mDcListAll.add(dataConnection);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mDcListAll=" + this.mDcListAll);
        printWriter.println(" mDcListActiveByCid=" + this.mDcListActiveByCid);
    }

    /* Access modifiers changed, original: protected */
    public String getWhatToString(int i) {
        String cmdToString = DataConnection.cmdToString(i);
        return cmdToString == null ? DcAsyncChannel.cmdToString(i) : cmdToString;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(getName(), str);
    }

    /* Access modifiers changed, original: 0000 */
    public void removeActiveDcByCid(DataConnection dataConnection) {
        if (((DataConnection) this.mDcListActiveByCid.remove(Integer.valueOf(dataConnection.mCid))) == null) {
            log("removeActiveDcByCid removedDc=null dc=" + dataConnection);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void removeDc(DataConnection dataConnection) {
        this.mDcListActiveByCid.remove(Integer.valueOf(dataConnection.mCid));
        this.mDcListAll.remove(dataConnection);
    }

    public String toString() {
        return "mDcListAll=" + this.mDcListAll + " mDcListActiveByCid=" + this.mDcListActiveByCid;
    }
}
