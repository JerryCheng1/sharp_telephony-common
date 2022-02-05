package com.android.internal.telephony.dataconnection;

import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkUtils;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.Rlog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DcController extends StateMachine {
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT = 1;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    static final int DATA_CONNECTION_ACTIVE_UNKNOWN = Integer.MAX_VALUE;
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private DcTesterDeactivateAll mDcTesterDeactivateAll;
    private DcTrackerBase mDct;
    private PhoneBase mPhone;
    ArrayList<DataConnection> mDcListAll = new ArrayList<>();
    private HashMap<Integer, DataConnection> mDcListActiveByCid = new HashMap<>();
    int mOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_UNKNOWN;
    private DccDefaultState mDccDefaultState = new DccDefaultState();

    private DcController(String name, PhoneBase phone, DcTrackerBase dct, Handler handler) {
        super(name, handler);
        setLogRecSize(300);
        log("E ctor");
        this.mPhone = phone;
        this.mDct = dct;
        addState(this.mDccDefaultState);
        setInitialState(this.mDccDefaultState);
        log("X ctor");
    }

    public static DcController makeDcc(PhoneBase phone, DcTrackerBase dct, Handler handler) {
        DcController dcc = new DcController("Dcc", phone, dct, handler);
        dcc.start();
        return dcc;
    }

    public void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    public void addDc(DataConnection dc) {
        this.mDcListAll.add(dc);
    }

    public void removeDc(DataConnection dc) {
        this.mDcListActiveByCid.remove(Integer.valueOf(dc.mCid));
        this.mDcListAll.remove(dc);
    }

    public void addActiveDcByCid(DataConnection dc) {
        if (dc.mCid < 0) {
            log("addActiveDcByCid dc.mCid < 0 dc=" + dc);
        }
        this.mDcListActiveByCid.put(Integer.valueOf(dc.mCid), dc);
    }

    public void removeActiveDcByCid(DataConnection dc) {
        if (this.mDcListActiveByCid.remove(Integer.valueOf(dc.mCid)) == null) {
            log("removeActiveDcByCid removedDc=null dc=" + dc);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DccDefaultState extends State {
        private DccDefaultState() {
            DcController.this = r1;
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

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 262149:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        DcController.this.log("DccDefaultState: msg.what=EVENT_RIL_CONNECTED mRilVersion=" + ar.result);
                        return true;
                    }
                    DcController.this.log("DccDefaultState: Unexpected exception on EVENT_RIL_CONNECTED");
                    return true;
                case 262150:
                default:
                    return true;
                case 262151:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    if (ar2.exception == null) {
                        onDataStateChanged((ArrayList) ar2.result);
                        return true;
                    }
                    DcController.this.log("DccDefaultState: EVENT_DATA_STATE_CHANGED: exception; likely radio not available, ignore");
                    return true;
            }
        }

        private void onDataStateChanged(ArrayList<DataCallResponse> dcsList) {
            int newOverallDataConnectionActiveState;
            int dcPowerState;
            DcController.this.lr("onDataStateChanged: dcsList=" + dcsList + " mDcListActiveByCid=" + DcController.this.mDcListActiveByCid);
            HashMap<Integer, DataCallResponse> dataCallResponseListByCid = new HashMap<>();
            Iterator i$ = dcsList.iterator();
            while (i$.hasNext()) {
                DataCallResponse dcs = i$.next();
                dataCallResponseListByCid.put(Integer.valueOf(dcs.cid), dcs);
            }
            ArrayList<DataConnection> dcsToRetry = new ArrayList<>();
            for (DataConnection dc : DcController.this.mDcListActiveByCid.values()) {
                if (dataCallResponseListByCid.get(Integer.valueOf(dc.mCid)) == null) {
                    DcController.this.log("onDataStateChanged: add to retry dc=" + dc);
                    dcsToRetry.add(dc);
                }
            }
            DcController.this.log("onDataStateChanged: dcsToRetry=" + dcsToRetry);
            ArrayList<ApnContext> apnsToCleanup = new ArrayList<>();
            boolean isAnyDataCallDormant = false;
            boolean isAnyDataCallActive = false;
            Iterator i$2 = dcsList.iterator();
            while (i$2.hasNext()) {
                DataCallResponse newState = i$2.next();
                DataConnection dc2 = (DataConnection) DcController.this.mDcListActiveByCid.get(Integer.valueOf(newState.cid));
                if (dc2 == null) {
                    DcController.this.loge("onDataStateChanged: no associated DC yet, ignore");
                } else {
                    if (dc2.mApnContexts.size() == 0) {
                        DcController.this.loge("onDataStateChanged: no connected apns, ignore");
                    } else {
                        DcController.this.log("onDataStateChanged: Found ConnId=" + newState.cid + " newState=" + newState.toString());
                        if (newState.active == 0) {
                            DcTrackerBase unused = DcController.this.mDct;
                            if (DcTrackerBase.mIsCleanupRequired) {
                                apnsToCleanup.addAll(dc2.mApnContexts);
                                DcTrackerBase unused2 = DcController.this.mDct;
                                DcTrackerBase.mIsCleanupRequired = false;
                            } else {
                                DcFailCause failCause = DcFailCause.fromInt(newState.status);
                                DcController.this.log("onDataStateChanged: inactive failCause=" + failCause);
                                if (failCause.isRestartRadioFail()) {
                                    DcController.this.log("onDataStateChanged: X restart radio");
                                    DcController.this.mDct.sendRestartRadio();
                                } else if (DcController.this.mDct.isPermanentFail(failCause)) {
                                    DcController.this.log("onDataStateChanged: inactive, add to cleanup list");
                                    apnsToCleanup.addAll(dc2.mApnContexts);
                                } else {
                                    DcController.this.log("onDataStateChanged: inactive, add to retry list");
                                    dcsToRetry.add(dc2);
                                }
                                if (TelBrand.IS_KDDI) {
                                    ArrayList<ApnContext> connectedApns = new ArrayList<>();
                                    for (ApnContext apnContext : dc2.mApnContexts) {
                                        if (apnContext.getState() == DctConstants.State.CONNECTED || apnContext.getState() == DctConstants.State.CONNECTING) {
                                            connectedApns.add(apnContext);
                                        }
                                    }
                                    Iterator i$3 = connectedApns.iterator();
                                    while (i$3.hasNext()) {
                                        DcController.this.mDct.sendOemKddiFailCauseBroadcast(DcFailCause.fromInt(newState.status), i$3.next());
                                    }
                                }
                            }
                        } else {
                            DataConnection.UpdateLinkPropertyResult result = dc2.updateLinkProperty(newState);
                            if (result.oldLp.equals(result.newLp)) {
                                DcController.this.log("onDataStateChanged: no change");
                            } else if (!result.oldLp.isIdenticalInterfaceName(result.newLp)) {
                                apnsToCleanup.addAll(dc2.mApnContexts);
                                DcController.this.log("onDataStateChanged: interface change, cleanup apns=" + dc2.mApnContexts);
                            } else if (!result.oldLp.isIdenticalDnses(result.newLp) || !result.oldLp.isIdenticalRoutes(result.newLp) || !result.oldLp.isIdenticalHttpProxy(result.newLp) || !result.oldLp.isIdenticalAddresses(result.newLp)) {
                                LinkProperties.CompareResult<LinkAddress> car = result.oldLp.compareAddresses(result.newLp);
                                DcController.this.log("onDataStateChanged: oldLp=" + result.oldLp + " newLp=" + result.newLp + " car=" + car);
                                boolean needToClean = false;
                                for (LinkAddress added : car.added) {
                                    Iterator i$4 = car.removed.iterator();
                                    while (true) {
                                        if (i$4.hasNext()) {
                                            if (NetworkUtils.addressTypeMatches(((LinkAddress) i$4.next()).getAddress(), added.getAddress())) {
                                                needToClean = true;
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }
                                if (needToClean) {
                                    DcController.this.log("onDataStateChanged: addr change, cleanup apns=" + dc2.mApnContexts + " oldLp=" + result.oldLp + " newLp=" + result.newLp);
                                    apnsToCleanup.addAll(dc2.mApnContexts);
                                } else {
                                    DcController.this.log("onDataStateChanged: simple change");
                                    for (ApnContext apnContext2 : dc2.mApnContexts) {
                                        DcController.this.mPhone.notifyDataConnection("linkPropertiesChanged", apnContext2.getApnType());
                                    }
                                }
                            } else {
                                DcController.this.log("onDataStateChanged: no changes");
                            }
                        }
                    }
                    if (newState.active == 2) {
                        isAnyDataCallActive = true;
                    }
                    if (newState.active == 1) {
                        isAnyDataCallDormant = true;
                    }
                }
            }
            int i = DcController.this.mOverallDataConnectionActiveState;
            if (!isAnyDataCallDormant || isAnyDataCallActive) {
                DcController.this.log("onDataStateChanged: Data Activity updated to NONE. isAnyDataCallActive = " + isAnyDataCallActive + " isAnyDataCallDormant = " + isAnyDataCallDormant);
                if (isAnyDataCallActive) {
                    newOverallDataConnectionActiveState = 2;
                    DcController.this.mDct.sendStartNetStatPoll(DctConstants.Activity.NONE);
                } else {
                    newOverallDataConnectionActiveState = 0;
                }
            } else {
                DcController.this.log("onDataStateChanged: Data Activity updated to DORMANT. stopNetStatePoll");
                DcController.this.mDct.sendStopNetStatPoll(DctConstants.Activity.DORMANT);
                newOverallDataConnectionActiveState = 1;
            }
            if (DcController.this.mOverallDataConnectionActiveState != newOverallDataConnectionActiveState) {
                DcController.this.mOverallDataConnectionActiveState = newOverallDataConnectionActiveState;
                long time = SystemClock.elapsedRealtimeNanos();
                switch (DcController.this.mOverallDataConnectionActiveState) {
                    case 0:
                    case 1:
                        dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
                        break;
                    case 2:
                        dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
                        break;
                    default:
                        dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_UNKNOWN;
                        break;
                }
                DataConnectionRealTimeInfo dcRtInfo = new DataConnectionRealTimeInfo(time, dcPowerState);
                DcController.this.log("onDataStateChanged: notify DcRtInfo changed dcRtInfo=" + dcRtInfo);
                DcController.this.mPhone.notifyDataConnectionRealTimeInfo(dcRtInfo);
            }
            DcController.this.lr("onDataStateChanged: dcsToRetry=" + dcsToRetry + " apnsToCleanup=" + apnsToCleanup);
            Iterator i$5 = apnsToCleanup.iterator();
            while (i$5.hasNext()) {
                DcController.this.mDct.sendCleanUpConnection(true, i$5.next());
            }
            Iterator i$6 = dcsToRetry.iterator();
            while (i$6.hasNext()) {
                DataConnection dc3 = i$6.next();
                DcController.this.log("onDataStateChanged: send EVENT_LOST_CONNECTION dc.mTag=" + dc3.mTag);
                dc3.sendMessage(262153, dc3.mTag);
            }
            DcController.this.log("onDataStateChanged: X");
        }
    }

    public void lr(String s) {
        logAndAddLogRec(s);
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected String getWhatToString(int what) {
        String info = DataConnection.cmdToString(what);
        if (info == null) {
            return DcAsyncChannel.cmdToString(what);
        }
        return info;
    }

    public String toString() {
        return "mDcListAll=" + this.mDcListAll + " mDcListActiveByCid=" + this.mDcListActiveByCid;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        DcController.super.dump(fd, pw, args);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDcListAll=" + this.mDcListAll);
        pw.println(" mDcListActiveByCid=" + this.mDcListActiveByCid);
    }
}
