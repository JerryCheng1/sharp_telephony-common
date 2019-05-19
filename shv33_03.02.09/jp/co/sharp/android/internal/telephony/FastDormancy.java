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
    /* renamed from: -jp-co-sharp-android-internal-telephony-FastDormancy$FastDormancyPollStateSwitchesValues */
    private static final /* synthetic */ int[] f32x2a08550f = null;
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
    private boolean mControlFDByScreenState = DBG;
    private int mDataState = -1;
    private final PhoneStateListener mFDPhoneStateListener = new PhoneStateListener() {
        public void onDataActivity(int direction) {
            boolean physicalLinkUp = FastDormancy.DBG;
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
                    physicalLinkUp = FastDormancy.DBG;
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

        public void onDataConnectionStateChanged(int state) {
            switch (state) {
                case 0:
                    FastDormancy.this.mIsPhysicalLinkUp = FastDormancy.DBG;
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
                        FastDormancy.this.mIsScreenOn = FastDormancy.DBG;
                        FastDormancy.this.startFastDormancyPollByScreenState();
                    }
                }
            }
        }
    };
    private boolean mFastDormancyRequested = DBG;
    private int mFastDormancyWaitingTime = FASTDORMANCY_DEFAULT_WAITING_TIME_EX;
    private boolean mIsEmulator = DBG;
    private boolean mIsPhysicalLinkUp = DBG;
    private boolean mIsScreenOn = true;
    private int mNoPacketDuration = 0;
    private Runnable mPollFastDormancy = new Runnable() {
        public void run() {
            long preTxPkts = TrafficStats.getMobileTxPackets();
            long preRxPkts = TrafficStats.getMobileRxPackets();
            long received = preRxPkts - FastDormancy.this.mRxPktsFastDormancy;
            if (preTxPkts - FastDormancy.this.mTxPktsFastDormancy == 0 && received == 0) {
                FastDormancy.this.procPacketCountsSame();
            } else {
                FastDormancy.this.procPacketCountsChanged();
            }
            FastDormancy.this.mTxPktsFastDormancy = preTxPkts;
            FastDormancy.this.mRxPktsFastDormancy = preRxPkts;
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

    private static /* synthetic */ int[] -getjp-co-sharp-android-internal-telephony-FastDormancy$FastDormancyPollStateSwitchesValues() {
        if (f32x2a08550f != null) {
            return f32x2a08550f;
        }
        int[] iArr = new int[FastDormancyPollState.values().length];
        try {
            iArr[FastDormancyPollState.POLLING_IDLE.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[FastDormancyPollState.POLLING_INITING.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[FastDormancyPollState.POLLING_WATCH_PACKETS.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[FastDormancyPollState.POLLING_WATCH_ZERO_PACKET.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        f32x2a08550f = iArr;
        return iArr;
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

    /* Access modifiers changed, original: protected */
    public void finalize() {
    }

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
        this.mFastDormancyRequested = DBG;
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
            this.mCm.invokeOemRilRequestRaw(new byte[]{(byte) 81, (byte) 79, (byte) 69, (byte) 77, (byte) 72, (byte) 79, (byte) 79, (byte) 75, (byte) 0, (byte) 8, (byte) 0, (byte) 3, (byte) 0, (byte) 0, (byte) 0, (byte) 0}, obtainMessage(1001));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onRequestedFastDormancy() {
        wakeLockRelease();
    }

    private boolean canFastDormancyRequest() {
        if (!isFastDormancyEnabled() || this.mIsEmulator || !this.mFastDormancyEnable || this.mFastDormancyRequested) {
            return DBG;
        }
        return true;
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

    private void procPacketCountsSame() {
        switch (-getjp-co-sharp-android-internal-telephony-FastDormancy$FastDormancyPollStateSwitchesValues()[this.mFastDormancyPollState.ordinal()]) {
            case 2:
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
            case 3:
                this.mNoPacketDuration = 1000;
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_ZERO_PACKET;
                return;
            case 4:
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

    private void procPacketCountsChanged() {
        switch (-getjp-co-sharp-android-internal-telephony-FastDormancy$FastDormancyPollStateSwitchesValues()[this.mFastDormancyPollState.ordinal()]) {
            case 2:
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
            case 3:
                wakeLockAcquire();
                this.mFastDormancyRequested = DBG;
                return;
            case 4:
                wakeLockAcquire();
                this.mFastDormancyRequested = DBG;
                this.mFastDormancyPollState = FastDormancyPollState.POLLING_WATCH_PACKETS;
                return;
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
        if (strRadioTechnology.startsWith("UMTS") || strRadioTechnology.startsWith("HSDPA") || strRadioTechnology.startsWith("HSUPA") || strRadioTechnology.startsWith("HSPA")) {
            return true;
        }
        return DBG;
    }

    /* Access modifiers changed, original: 0000 */
    public void startFastDormancyPollByScreenState() {
        if (!getControlFDByScreenState()) {
            setFastDormancyWaitingTime(FASTDORMANCY_DEFAULT_WAITING_TIME);
        } else if (this.mIsPhysicalLinkUp) {
            startFastDormancyPoll();
        }
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
