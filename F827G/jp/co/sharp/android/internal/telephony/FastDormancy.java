package jp.co.sharp.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelBrand;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class FastDormancy extends Handler {
    private static final boolean DBG = false;
    private static final int EVENT_FASTDORMANCY_REQUESTED = 1001;
    public static final int FASTDORMANCY_DEFAULT_WAITING_TIME;
    public static final int FASTDORMANCY_DEFAULT_WAITING_TIME_EX;
    private static final int FASTDORMANCY_POLL_PERIOD = 1000;
    private static final String LOG_TAG = "FastDormancy";
    private static final String WAKELOCK_TAG = "FastDormancy";
    private CommandsInterface mCm;
    private Context mContext;
    private boolean mIsEmulator;
    private PowerManager.WakeLock mWakeLock;
    private ConnectivityManager connectivityManager = null;
    private FastDormancyPollState mFastDormancyPollState = FastDormancyPollState.POLLING_IDLE;
    private long mTxPktsFastDormancy = 0;
    private long mRxPktsFastDormancy = 0;
    private int mNoPacketDuration = 0;
    private int mFastDormancyWaitingTime = FASTDORMANCY_DEFAULT_WAITING_TIME_EX;
    private boolean mFastDormancyEnable = true;
    private boolean mFastDormancyRequested = false;
    private boolean mControlFDByScreenState = false;
    private int mFastDormancyLockCount = 0;
    private int mDataState = -1;
    private boolean mIsPhysicalLinkUp = false;
    private boolean mIsScreenOn = true;
    private final BroadcastReceiver mFastDormancyReceiver = new BroadcastReceiver() { // from class: jp.co.sharp.android.internal.telephony.FastDormancy.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action;
            if (intent != null && (action = intent.getAction()) != null) {
                if ("android.intent.action.SCREEN_ON".endsWith(action)) {
                    FastDormancy.this.mIsScreenOn = true;
                    FastDormancy.this.stopFastDormancyPollByScreenState();
                } else if ("android.intent.action.SCREEN_OFF".endsWith(action)) {
                    FastDormancy.this.mIsScreenOn = false;
                    FastDormancy.this.startFastDormancyPollByScreenState();
                }
            }
        }
    };
    private final PhoneStateListener mFDPhoneStateListener = new PhoneStateListener() { // from class: jp.co.sharp.android.internal.telephony.FastDormancy.2
        @Override // android.telephony.PhoneStateListener
        public void onDataActivity(int direction) {
            boolean physicalLinkUp = false;
            switch (direction) {
                case 0:
                case 1:
                case 2:
                case 3:
                    if (FastDormancy.this.mDataState == 2) {
                        physicalLinkUp = true;
                        break;
                    }
                    break;
                case 4:
                    physicalLinkUp = false;
                    break;
                default:
                    return;
            }
            if (FastDormancy.this.mIsPhysicalLinkUp != physicalLinkUp) {
                FastDormancy.this.mIsPhysicalLinkUp = physicalLinkUp;
                if (!physicalLinkUp) {
                    FastDormancy.this.stopFastDormancyPoll();
                } else if (!FastDormancy.this.getControlFDByScreenState()) {
                    FastDormancy.this.startFastDormancyPoll();
                } else if (!FastDormancy.this.mIsScreenOn) {
                    FastDormancy.this.startFastDormancyPoll();
                }
            }
        }

        @Override // android.telephony.PhoneStateListener
        public void onDataConnectionStateChanged(int state) {
            switch (state) {
                case 0:
                    FastDormancy.this.mIsPhysicalLinkUp = false;
                    FastDormancy.this.stopFastDormancyPoll();
                    break;
                case 2:
                    if (FastDormancy.this.mFastDormancyPollState == FastDormancyPollState.POLLING_IDLE) {
                        FastDormancy.this.mIsPhysicalLinkUp = true;
                        FastDormancy.this.startFastDormancyPoll();
                        break;
                    }
                    break;
            }
            FastDormancy.this.mDataState = state;
        }
    };
    private Runnable mPollFastDormancy = new Runnable() { // from class: jp.co.sharp.android.internal.telephony.FastDormancy.3
        @Override // java.lang.Runnable
        public void run() {
            long preTxPkts = TrafficStats.getMobileTxPackets();
            long preRxPkts = TrafficStats.getMobileRxPackets();
            long sent = preTxPkts - FastDormancy.this.mTxPktsFastDormancy;
            long received = preRxPkts - FastDormancy.this.mRxPktsFastDormancy;
            if (sent == 0 && received == 0) {
                FastDormancy.this.procPacketCountsSame();
            } else {
                FastDormancy.this.procPacketCountsChanged();
            }
            FastDormancy.this.mTxPktsFastDormancy = preTxPkts;
            FastDormancy.this.mRxPktsFastDormancy = preRxPkts;
            if (FastDormancy.this.mFastDormancyPollState != FastDormancyPollState.POLLING_IDLE) {
                FastDormancy.this.postDelayed(this, 1000L);
            }
        }
    };

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum FastDormancyPollState {
        POLLING_IDLE,
        POLLING_INITING,
        POLLING_WATCH_PACKETS,
        POLLING_WATCH_ZERO_PACKET
    }

    static {
        if (TelBrand.IS_DCM) {
            FASTDORMANCY_DEFAULT_WAITING_TIME = 5000;
            FASTDORMANCY_DEFAULT_WAITING_TIME_EX = 5000;
            return;
        }
        FASTDORMANCY_DEFAULT_WAITING_TIME = 4000;
        FASTDORMANCY_DEFAULT_WAITING_TIME_EX = 7000;
    }

    public FastDormancy(CommandsInterface ci, Context context) {
        this.mIsEmulator = false;
        this.mCm = ci;
        this.mContext = context;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "FastDormancy");
        IntentFilter fd_intentFilter = new IntentFilter();
        fd_intentFilter.addAction("android.intent.action.SCREEN_ON");
        fd_intentFilter.addAction("android.intent.action.SCREEN_OFF");
        context.registerReceiver(this.mFastDormancyReceiver, fd_intentFilter);
        ((TelephonyManager) context.getSystemService("phone")).listen(this.mFDPhoneStateListener, 192);
        if (SystemProperties.get("ro.hardware").equals("goldfish")) {
            this.mIsEmulator = true;
        }
    }

    public void dispose() {
    }

    protected void finalize() {
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1001:
                onRequestedFastDormancy();
                return;
            default:
                return;
        }
    }

    public void startFastDormancyPoll() {
        if (this.mFastDormancyPollState != FastDormancyPollState.POLLING_IDLE) {
            stopFastDormancyPoll();
        }
        this.mNoPacketDuration = 0;
        this.mFastDormancyRequested = false;
        this.mFastDormancyPollState = FastDormancyPollState.POLLING_INITING;
        wakeLockAcquire();
        this.mPollFastDormancy.run();
    }

    public void stopFastDormancyPoll() {
        this.mFastDormancyPollState = FastDormancyPollState.POLLING_IDLE;
        removeCallbacks(this.mPollFastDormancy);
        wakeLockRelease();
    }

    private void requestFastDormancy() {
        try {
            this.mCm.invokeOemRilRequestRaw(new byte[]{81, 79, 69, 77, 72, 79, 79, 75, 3, 0, 8, 0, 0, 0, 0, 0}, obtainMessage(1001));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onRequestedFastDormancy() {
        wakeLockRelease();
    }

    private boolean canFastDormancyRequest() {
        return isFastDormancyEnabled() && !this.mIsEmulator && this.mFastDormancyEnable && !this.mFastDormancyRequested;
    }

    private void wakeLockAcquire() {
        if (this.mFastDormancyLockCount == 0 && this.mFastDormancyPollState != FastDormancyPollState.POLLING_IDLE) {
            this.mWakeLock.acquire();
            this.mFastDormancyLockCount++;
        }
    }

    private void wakeLockRelease() {
        if (this.mFastDormancyLockCount != 0) {
            this.mFastDormancyLockCount--;
            this.mWakeLock.release();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void procPacketCountsSame() {
        switch (this.mFastDormancyPollState) {
            case POLLING_INITING:
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
            case POLLING_WATCH_PACKETS:
                this.mNoPacketDuration = 1000;
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_ZERO_PACKET;
                return;
            case POLLING_WATCH_ZERO_PACKET:
                this.mNoPacketDuration += 1000;
                if (this.mNoPacketDuration >= this.mFastDormancyWaitingTime) {
                    if (this.connectivityManager == null) {
                        this.connectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
                    }
                    if (this.connectivityManager == null) {
                        return;
                    }
                    if (this.connectivityManager.getTetheredIfaces().length != 0) {
                        this.mNoPacketDuration = 0;
                        return;
                    } else if (canFastDormancyRequest()) {
                        requestFastDormancy();
                        this.mNoPacketDuration = 0;
                        this.mFastDormancyRequested = true;
                        return;
                    } else {
                        wakeLockRelease();
                        return;
                    }
                } else {
                    return;
                }
            case POLLING_IDLE:
            default:
                return;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void procPacketCountsChanged() {
        switch (this.mFastDormancyPollState) {
            case POLLING_INITING:
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
            case POLLING_WATCH_PACKETS:
                wakeLockAcquire();
                this.mFastDormancyRequested = false;
                return;
            case POLLING_WATCH_ZERO_PACKET:
                wakeLockAcquire();
                this.mFastDormancyRequested = false;
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
            case POLLING_IDLE:
            default:
                return;
        }
    }

    public boolean getControlFDByScreenState() {
        return this.mControlFDByScreenState;
    }

    public void setFastDormancyWaitingTime(int time) {
        this.mFastDormancyWaitingTime = time;
    }

    private boolean isFastDormancyEnabled() {
        String strRadioTechnology = SystemProperties.get("gsm.network.type", "");
        return strRadioTechnology.startsWith("UMTS") || strRadioTechnology.startsWith("HSDPA") || strRadioTechnology.startsWith("HSUPA") || strRadioTechnology.startsWith("HSPA");
    }

    void startFastDormancyPollByScreenState() {
        if (!getControlFDByScreenState()) {
            setFastDormancyWaitingTime(FASTDORMANCY_DEFAULT_WAITING_TIME);
        } else if (this.mIsPhysicalLinkUp) {
            startFastDormancyPoll();
        }
    }

    void stopFastDormancyPollByScreenState() {
        if (getControlFDByScreenState()) {
            stopFastDormancyPoll();
        } else {
            setFastDormancyWaitingTime(FASTDORMANCY_DEFAULT_WAITING_TIME_EX);
        }
    }
}
