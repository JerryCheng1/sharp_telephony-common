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
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelBrand;

public final class FastDormancy extends Handler {
    private static final boolean DBG = false;
    private static final int EVENT_FASTDORMANCY_REQUESTED = 1001;
    public static final int FASTDORMANCY_DEFAULT_WAITING_TIME;
    public static final int FASTDORMANCY_DEFAULT_WAITING_TIME_EX;
    private static final int FASTDORMANCY_POLL_PERIOD = 1000;
    private static final String LOG_TAG = "FastDormancy";
    private static final String WAKELOCK_TAG = "FastDormancy";
    private ConnectivityManager connectivityManager = null;
    private CommandsInterface mCm;
    private Context mContext;
    private boolean mControlFDByScreenState = false;
    private int mDataState = -1;
    private final PhoneStateListener mFDPhoneStateListener = new PhoneStateListener() {
        public void onDataActivity(int i) {
            boolean z = false;
            switch (i) {
                case 0:
                case 1:
                case 2:
                case 3:
                    if (FastDormancy.this.mDataState == 2) {
                        z = true;
                        break;
                    }
                    break;
                case 4:
                    break;
                default:
                    return;
            }
            if (FastDormancy.this.mIsPhysicalLinkUp != z) {
                FastDormancy.this.mIsPhysicalLinkUp = z;
                if (!z) {
                    FastDormancy.this.stopFastDormancyPoll();
                } else if (!FastDormancy.this.getControlFDByScreenState()) {
                    FastDormancy.this.startFastDormancyPoll();
                } else if (!FastDormancy.this.mIsScreenOn) {
                    FastDormancy.this.startFastDormancyPoll();
                }
            }
        }

        public void onDataConnectionStateChanged(int i) {
            switch (i) {
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
            FastDormancy.this.mDataState = i;
        }
    };
    private boolean mFastDormancyEnable = true;
    private int mFastDormancyLockCount = 0;
    private FastDormancyPollState mFastDormancyPollState = FastDormancyPollState.POLLING_IDLE;
    private final BroadcastReceiver mFastDormancyReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (action != null) {
                    if ("android.intent.action.SCREEN_ON".endsWith(action)) {
                        FastDormancy.this.mIsScreenOn = true;
                        FastDormancy.this.stopFastDormancyPollByScreenState();
                    } else if ("android.intent.action.SCREEN_OFF".endsWith(action)) {
                        FastDormancy.this.mIsScreenOn = false;
                        FastDormancy.this.startFastDormancyPollByScreenState();
                    }
                }
            }
        }
    };
    private boolean mFastDormancyRequested = false;
    private int mFastDormancyWaitingTime = FASTDORMANCY_DEFAULT_WAITING_TIME_EX;
    private boolean mIsEmulator = false;
    private boolean mIsPhysicalLinkUp = false;
    private boolean mIsScreenOn = true;
    private int mNoPacketDuration = 0;
    private Runnable mPollFastDormancy = new Runnable() {
        public void run() {
            long mobileTxPackets = TrafficStats.getMobileTxPackets();
            long mobileRxPackets = TrafficStats.getMobileRxPackets();
            long access$400 = FastDormancy.this.mTxPktsFastDormancy;
            long access$500 = FastDormancy.this.mRxPktsFastDormancy;
            if (mobileTxPackets - access$400 == 0 && mobileRxPackets - access$500 == 0) {
                FastDormancy.this.procPacketCountsSame();
            } else {
                FastDormancy.this.procPacketCountsChanged();
            }
            FastDormancy.this.mTxPktsFastDormancy = mobileTxPackets;
            FastDormancy.this.mRxPktsFastDormancy = mobileRxPackets;
            if (FastDormancy.this.mFastDormancyPollState != FastDormancyPollState.POLLING_IDLE) {
                FastDormancy.this.postDelayed(this, 1000);
            }
        }
    };
    private long mRxPktsFastDormancy = 0;
    private long mTxPktsFastDormancy = 0;
    private WakeLock mWakeLock;

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

    public FastDormancy(CommandsInterface commandsInterface, Context context) {
        this.mCm = commandsInterface;
        this.mContext = context;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "FastDormancy");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        context.registerReceiver(this.mFastDormancyReceiver, intentFilter);
        ((TelephonyManager) context.getSystemService("phone")).listen(this.mFDPhoneStateListener, 192);
        if (SystemProperties.get("ro.hardware").equals("goldfish")) {
            this.mIsEmulator = true;
        }
    }

    private boolean canFastDormancyRequest() {
        return isFastDormancyEnabled() && !this.mIsEmulator && this.mFastDormancyEnable && !this.mFastDormancyRequested;
    }

    private boolean isFastDormancyEnabled() {
        String str = SystemProperties.get("gsm.network.type", "");
        return str.startsWith("UMTS") || str.startsWith("HSDPA") || str.startsWith("HSUPA") || str.startsWith("HSPA");
    }

    private void onRequestedFastDormancy() {
        wakeLockRelease();
    }

    private void procPacketCountsChanged() {
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
            default:
                return;
        }
    }

    private void procPacketCountsSame() {
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
                }
                return;
            default:
                return;
        }
    }

    private void requestFastDormancy() {
        try {
            this.mCm.invokeOemRilRequestRaw(new byte[]{(byte) 81, (byte) 79, (byte) 69, (byte) 77, (byte) 72, (byte) 79, (byte) 79, (byte) 75, (byte) 3, (byte) 0, (byte) 8, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0}, obtainMessage(1001));
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public void dispose() {
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
    }

    public boolean getControlFDByScreenState() {
        return this.mControlFDByScreenState;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1001:
                onRequestedFastDormancy();
                return;
            default:
                return;
        }
    }

    public void setFastDormancyWaitingTime(int i) {
        this.mFastDormancyWaitingTime = i;
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

    /* Access modifiers changed, original: 0000 */
    public void startFastDormancyPollByScreenState() {
        if (!getControlFDByScreenState()) {
            setFastDormancyWaitingTime(FASTDORMANCY_DEFAULT_WAITING_TIME);
        } else if (this.mIsPhysicalLinkUp) {
            startFastDormancyPoll();
        }
    }

    public void stopFastDormancyPoll() {
        this.mFastDormancyPollState = FastDormancyPollState.POLLING_IDLE;
        removeCallbacks(this.mPollFastDormancy);
        wakeLockRelease();
    }

    /* Access modifiers changed, original: 0000 */
    public void stopFastDormancyPollByScreenState() {
        if (getControlFDByScreenState()) {
            stopFastDormancyPoll();
        } else {
            setFastDormancyWaitingTime(FASTDORMANCY_DEFAULT_WAITING_TIME_EX);
        }
    }
}
