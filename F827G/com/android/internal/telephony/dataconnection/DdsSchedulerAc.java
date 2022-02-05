package com.android.internal.telephony.dataconnection;

import android.net.NetworkRequest;
import android.telephony.Rlog;
import com.android.internal.util.AsyncChannel;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DdsSchedulerAc extends AsyncChannel {
    public static final int BASE = 540672;
    public static final int EVENT_ADD_REQUEST = 540677;
    public static final int EVENT_MODEM_DATA_CAPABILITY_UPDATE = 540676;
    public static final int EVENT_ON_DEMAND_DDS_SWITCH_DONE = 540674;
    public static final int EVENT_ON_DEMAND_PS_ATTACH_DONE = 540675;
    public static final int EVENT_REMOVE_REQUEST = 540678;
    public static final int REQ_DDS_ALLOCATION = 540672;
    public static final int REQ_DDS_FREE = 540673;
    private final String TAG = "DdsSchedulerAc";
    private DdsScheduler mScheduler;

    public void allocateDds(NetworkRequest req) {
        Rlog.d("DdsSchedulerAc", "EVENT_ADD_REQUEST = " + req);
        sendMessage(EVENT_ADD_REQUEST, req);
    }

    public void freeDds(NetworkRequest req) {
        Rlog.d("DdsSchedulerAc", "EVENT_REMOVE_REQUEST = " + req);
        sendMessage(EVENT_REMOVE_REQUEST, req);
    }
}
