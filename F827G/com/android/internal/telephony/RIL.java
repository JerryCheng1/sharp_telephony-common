package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.cdma.SignalToneUtil;
import com.android.internal.telephony.dataconnection.ApnProfileOmh;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SsData;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SpnOverride;
import com.google.android.mms.pdu.CharacterSets;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class RIL extends BaseCommands implements CommandsInterface {
    private static final int BYTE_SIZE = 1;
    private static final int CDMA_BROADCAST_SMS_NO_OF_SERVICE_CATEGORIES = 31;
    private static final int CDMA_BSI_NO_OF_INTS_STRUCT = 3;
    public static final String COLUM_TABLE_MCN_SETTING_DURATION_TIME = "COLUM_TABLE_MCN_SETTING_DURATION_TIME";
    public static final String COLUM_TABLE_MCN_SETTING_DURATION_TYPE = "COLUM_TABLE_MCN_SETTING_DURATION_TYPE";
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT = 60000;
    static final int EVENT_SEARCHING_NETWORK_DELAY = 4;
    static final int EVENT_SEND = 1;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    private static final int INT_SIZE = 4;
    private static final int OEMHOOK_BASE = 524288;
    private static final int OEMHOOK_EVT_HOOK_GET_MODEM_CAPABILITY = 524323;
    private static final int OEMHOOK_EVT_HOOK_SET_LOCAL_CALL_HOLD = 524301;
    private static final int OEMHOOK_EVT_HOOK_UPDATE_SUB_BINDING = 524324;
    private static final String OEM_IDENTIFIER = "QOEMHOOK";
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_UNSOLICITED = 1;
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final int RIL_MAX_COMMAND_BYTES = 8192;
    static final long SEARCH_NETWORK_DELAY_TIME = 3000;
    private static final boolean SMARTCARD_DBG = false;
    static final int SOCKET_OPEN_RETRY_MILLIS = 4000;
    final int OEMHOOK_UNSOL_SIM_REFRESH;
    final int OEMHOOK_UNSOL_WWAN_IWLAN_COEXIST;
    final int QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY;
    Display mDefaultDisplay;
    int mDefaultDisplayState;
    private final DisplayManager.DisplayListener mDisplayListener;
    int mHeaderSize;
    private Integer mInstanceId;
    BroadcastReceiver mIntentReceiver;
    Object mLastNITZTimeInfo;
    private boolean mLimitationByChameleon;
    private boolean mLimitedService;
    private boolean mRILConnected_SetInitialAttachApn_OnceSkip;
    private boolean mRILConnected_UpdateOemDataSettings_OnceSkip;
    RILReceiver mReceiver;
    Thread mReceiverThread;
    SparseArray<RILRequest> mRequestList;
    RILSender mSender;
    HandlerThread mSenderThread;
    private String mSetInitialAttachApn_Apn;
    private int mSetInitialAttachApn_AuthType;
    private String mSetInitialAttachApn_Password;
    private String mSetInitialAttachApn_Protocol;
    private String mSetInitialAttachApn_Username;
    private boolean mSetLimitationByChameleon;
    LocalSocket mSocket;
    AtomicBoolean mTestingEmergencyCall;
    private boolean mUpdateOemDataSettings_DataRoaming;
    private boolean mUpdateOemDataSettings_MobileData;
    PowerManager.WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;
    static final boolean LOG_SD = SystemProperties.getBoolean("persist.sharp.telephony.nvlogsd", false);
    static final boolean LOG_NW = SystemProperties.getBoolean("persist.sharp.telephony.nvlognw", false);
    static final boolean LOG_NS = LOG_NW;
    static final String[] SOCKET_NAME_RIL = {"rild", "rild2", "rild3"};

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class RILSender extends Handler implements Runnable {
        byte[] dataLength = new byte[4];

        public RILSender(Looper looper) {
            super(looper);
        }

        @Override // java.lang.Runnable
        public void run() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            RILRequest rr = (RILRequest) msg.obj;
            switch (msg.what) {
                case 1:
                    try {
                        LocalSocket s = RIL.this.mSocket;
                        if (s == null) {
                            rr.onError(1, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        synchronized (RIL.this.mRequestList) {
                            RIL.this.mRequestList.append(rr.mSerial, rr);
                        }
                        byte[] data = rr.mParcel.marshall();
                        rr.mParcel.recycle();
                        rr.mParcel = null;
                        if (data.length > 8192) {
                            throw new RuntimeException("Parcel larger than max bytes allowed! " + data.length);
                        }
                        byte[] bArr = this.dataLength;
                        this.dataLength[1] = 0;
                        bArr[0] = 0;
                        this.dataLength[2] = (byte) ((data.length >> 8) & 255);
                        this.dataLength[3] = (byte) (data.length & 255);
                        s.getOutputStream().write(this.dataLength);
                        s.getOutputStream().write(data);
                        return;
                    } catch (IOException ex) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "IOException", ex);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(1, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    } catch (RuntimeException exc) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception ", exc);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(2, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    }
                case 2:
                    synchronized (RIL.this.mRequestList) {
                        if (RIL.this.clearWakeLock()) {
                            int count = RIL.this.mRequestList.size();
                            Rlog.d(RIL.RILJ_LOG_TAG, "WAKE_LOCK_TIMEOUT  mRequestList=" + count);
                            for (int i = 0; i < count; i++) {
                                RILRequest rr2 = RIL.this.mRequestList.valueAt(i);
                                Rlog.d(RIL.RILJ_LOG_TAG, i + ": [" + rr2.mSerial + "] " + RIL.requestToString(rr2.mRequest));
                            }
                        }
                    }
                    return;
                case 3:
                default:
                    return;
                case 4:
                    if (TelBrand.IS_DCM) {
                        RIL.this.send(rr);
                        return;
                    }
                    return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int readRilMessage(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = 4;
        do {
            int countRead = is.read(buffer, offset, remaining);
            if (countRead < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message length");
                return -1;
            }
            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);
        int messageLength = ((buffer[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 24) | ((buffer[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 16) | ((buffer[2] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (buffer[3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
        int offset2 = 0;
        int remaining2 = messageLength;
        do {
            int countRead2 = is.read(buffer, offset2, remaining2);
            if (countRead2 < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message.  messageLength=" + messageLength + " remaining=" + remaining2);
                return -1;
            }
            offset2 += countRead2;
            remaining2 -= countRead2;
        } while (remaining2 > 0);
        return messageLength;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    class RILReceiver implements Runnable {
        byte[] buffer = new byte[8192];

        RILReceiver() {
        }

        @Override // java.lang.Runnable
        public void run() {
            String rilSocket;
            int retryCount = 0;
            while (true) {
                LocalSocket s = null;
                try {
                    if (RIL.this.mInstanceId == null || RIL.this.mInstanceId.intValue() == 0) {
                        rilSocket = RIL.SOCKET_NAME_RIL[0];
                    } else {
                        rilSocket = RIL.SOCKET_NAME_RIL[RIL.this.mInstanceId.intValue()];
                    }
                    try {
                        LocalSocket s2 = new LocalSocket();
                        try {
                            try {
                                s2.connect(new LocalSocketAddress(rilSocket, LocalSocketAddress.Namespace.RESERVED));
                                retryCount = 0;
                                RIL.this.mSocket = s2;
                                Rlog.i(RIL.RILJ_LOG_TAG, "(" + RIL.this.mInstanceId + ") Connected to '" + rilSocket + "' socket");
                                try {
                                    InputStream is = RIL.this.mSocket.getInputStream();
                                    while (true) {
                                        int length = RIL.readRilMessage(is, this.buffer);
                                        if (length < 0) {
                                            break;
                                        }
                                        Parcel p = Parcel.obtain();
                                        p.unmarshall(this.buffer, 0, length);
                                        p.setDataPosition(0);
                                        RIL.this.processResponse(p);
                                        p.recycle();
                                    }
                                } catch (IOException ex) {
                                    Rlog.i(RIL.RILJ_LOG_TAG, "'" + rilSocket + "' socket closed", ex);
                                } catch (Throwable tr) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception read length=0Exception:" + tr.toString());
                                }
                                Rlog.i(RIL.RILJ_LOG_TAG, "(" + RIL.this.mInstanceId + ") Disconnected from '" + rilSocket + "' socket");
                                RIL.this.setRadioState(CommandsInterface.RadioState.RADIO_UNAVAILABLE);
                                try {
                                    RIL.this.mSocket.close();
                                } catch (IOException e) {
                                }
                                RIL.this.mSocket = null;
                                RILRequest.resetSerial();
                                RIL.this.clearRequestList(1, false);
                            } catch (IOException e2) {
                                s = s2;
                                if (s != null) {
                                    try {
                                        s.close();
                                    } catch (IOException e3) {
                                    }
                                }
                                if (retryCount == 8) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket after " + retryCount + " times, continuing to retry silently");
                                } else if (retryCount >= 0 && retryCount < 8) {
                                    Rlog.i(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket; retrying after timeout");
                                }
                                try {
                                    Thread.sleep(4000L);
                                } catch (InterruptedException e4) {
                                }
                                retryCount++;
                            }
                        } catch (Throwable th) {
                            tr = th;
                            Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception", tr);
                            RIL.this.notifyRegistrantsRilConnectionChanged(-1);
                            return;
                        }
                    } catch (IOException e5) {
                    }
                } catch (Throwable th2) {
                    tr = th2;
                }
            }
        }
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        this.OEMHOOK_UNSOL_SIM_REFRESH = 525304;
        this.OEMHOOK_UNSOL_WWAN_IWLAN_COEXIST = 525306;
        this.mHeaderSize = OEM_IDENTIFIER.length() + 8;
        this.QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = 525308;
        this.mDefaultDisplayState = 0;
        this.mRequestList = new SparseArray<>();
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mLimitedService = false;
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = true;
        this.mUpdateOemDataSettings_MobileData = false;
        this.mUpdateOemDataSettings_DataRoaming = false;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = true;
        this.mSetInitialAttachApn_Apn = null;
        this.mSetInitialAttachApn_Protocol = null;
        this.mSetInitialAttachApn_AuthType = -1;
        this.mSetInitialAttachApn_Username = null;
        this.mSetInitialAttachApn_Password = null;
        this.mSetLimitationByChameleon = false;
        this.mLimitationByChameleon = true;
        this.mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.RIL.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                if (intent.getAction().equals("android.intent.action.SERVICE_STATE")) {
                    RIL.this.upDomesticInService(context2, intent);
                } else if (!TelBrand.IS_DCM || !intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN")) {
                    Rlog.w(RIL.RILJ_LOG_TAG, "RIL received unexpected Intent: " + intent.getAction());
                } else {
                    Thread thread = new Thread(new RILRadioLogWriter(1, 1));
                    if (thread != null) {
                        thread.start();
                    }
                }
            }
        };
        this.mDisplayListener = new DisplayManager.DisplayListener() { // from class: com.android.internal.telephony.RIL.2
            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayAdded(int displayId) {
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayRemoved(int displayId) {
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayChanged(int displayId) {
                if (displayId == 0) {
                    RIL.this.updateScreenState();
                }
            }
        };
        riljLog("RIL(context, preferredNetworkType=" + preferredNetworkType + " cdmaSubscription=" + cdmaSubscription + ")");
        this.mContext = context;
        this.mCdmaSubscription = cdmaSubscription;
        this.mPreferredNetworkType = preferredNetworkType;
        this.mPhoneType = 0;
        this.mInstanceId = instanceId;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, RILJ_LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mWakeLockCount = 0;
        this.mSenderThread = new HandlerThread("RILSender" + this.mInstanceId);
        this.mSenderThread.start();
        this.mSender = new RILSender(this.mSenderThread.getLooper());
        if (!((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0)) {
            riljLog("Not starting RILReceiver: wifi-only");
        } else {
            riljLog("Starting RILReceiver" + this.mInstanceId);
            this.mReceiver = new RILReceiver();
            this.mReceiverThread = new Thread(this.mReceiver, "RILReceiver" + this.mInstanceId);
            this.mReceiverThread.start();
            DisplayManager dm = (DisplayManager) context.getSystemService("display");
            this.mDefaultDisplay = dm.getDisplay(0);
            dm.registerDisplayListener(this.mDisplayListener, null);
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.SERVICE_STATE");
            if (TelBrand.IS_DCM && LOG_SD) {
                filter.addAction("android.intent.action.ACTION_SHUTDOWN");
            }
            context.registerReceiver(this.mIntentReceiver, filter);
        }
        TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
        this.mLimitedService = this.mLimitationByChameleon;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRadioTechnology(Message result) {
        RILRequest rr = RILRequest.obtain(OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getImsRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(112, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult((Object) null, this.mLastNITZTimeInfo, (Throwable) null));
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIccCardStatus(Message result) {
        RILRequest rr = RILRequest.obtain(1, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message result) {
        RILRequest rr = RILRequest.obtain(122, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " slot: " + slotId + " appIndex: " + appIndex + " subId: " + subId + " subStatus: " + subStatus);
        rr.mParcel.writeInt(slotId);
        rr.mParcel.writeInt(appIndex);
        rr.mParcel.writeInt(subId);
        rr.mParcel.writeInt(subStatus);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setDataAllowed(boolean allowed, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(123, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + allowed);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!allowed) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getDataCallProfile(int appType, Message result) {
        RILRequest rr = RILRequest.obtain(132, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(appType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + appType);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPinForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(2, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(3, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2ForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(4, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2ForApp(String puk, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(5, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPinForApp(String oldPin, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(6, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(7, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin2);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        RILRequest rr = RILRequest.obtain(44, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(oldPwd);
        rr.mParcel.writeString(newPwd);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyDepersonalization(String netpin, String type, Message result) {
        RILRequest rr = RILRequest.obtain(8, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " Type:" + type);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(type);
        rr.mParcel.writeString(netpin);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCurrentCalls(Message result) {
        RILRequest rr = RILRequest.obtain(9, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataCallList(Message result) {
        RILRequest rr = RILRequest.obtain(57, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(10, result);
        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        if (uusInfo == null) {
            rr.mParcel.writeInt(0);
        } else {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSIForApp(String aid, Message result) {
        RILRequest rr = RILRequest.obtain(11, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> getIMSI: " + requestToString(rr.mRequest) + " aid: " + aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMEI(Message result) {
        RILRequest rr = RILRequest.obtain(38, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMEISV(Message result) {
        RILRequest rr = RILRequest.obtain(39, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupConnection(int gsmIndex, Message result) {
        riljLog("hangupConnection: gsmIndex=" + gsmIndex);
        RILRequest rr = RILRequest.obtain(12, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupWaitingOrBackground(Message result) {
        RILRequest rr = RILRequest.obtain(13, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupForegroundResumeBackground(Message result) {
        RILRequest rr = RILRequest.obtain(14, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void switchWaitingOrHoldingAndActive(Message result) {
        RILRequest rr = RILRequest.obtain(15, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void conference(Message result) {
        RILRequest rr = RILRequest.obtain(16, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(82, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredVoicePrivacy(Message result) {
        send(RILRequest.obtain(83, result));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void separateConnection(int gsmIndex, Message result) {
        RILRequest rr = RILRequest.obtain(52, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acceptCall(Message result) {
        RILRequest rr = RILRequest.obtain(40, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void rejectCall(Message result) {
        RILRequest rr = RILRequest.obtain(17, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void explicitCallTransfer(Message result) {
        RILRequest rr = RILRequest.obtain(72, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getLastCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(18, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    @Deprecated
    public void getLastPdpFailCause(Message result) {
        getLastDataCallFailCause(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getLastDataCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(56, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setMute(boolean enableMute, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(53, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enableMute);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enableMute) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getMute(Message response) {
        RILRequest rr = RILRequest.obtain(54, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSignalStrength(Message result) {
        RILRequest rr = RILRequest.obtain(19, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(20, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(21, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getOperator(Message result) {
        RILRequest rr = RILRequest.obtain(22, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getHardwareConfig(Message result) {
        RILRequest rr = RILRequest.obtain(124, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(24, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(49, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopDtmf(Message result) {
        RILRequest rr = RILRequest.obtain(50, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        RILRequest rr = RILRequest.obtain(85, result);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(dtmfString);
        rr.mParcel.writeString(Integer.toString(on));
        rr.mParcel.writeString(Integer.toString(off));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + dtmfString);
        send(rr);
    }

    private void constructGsmSendSmsRilRequest(RILRequest rr, String smscPDU, String pdu) {
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(smscPDU);
        rr.mParcel.writeString(pdu);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendSMS(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(25, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(26, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private void constructCdmaSendSmsRilRequest(RILRequest rr, byte[] pdu) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        try {
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeByte((byte) dis.readInt());
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            int address_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) address_nbr_of_digits);
            for (int i = 0; i < address_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeByte((byte) dis.read());
            int subaddr_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) subaddr_nbr_of_digits);
            for (int i2 = 0; i2 < subaddr_nbr_of_digits; i2++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            int bearerDataLength = dis.read();
            rr.mParcel.writeInt(bearerDataLength);
            for (int i3 = 0; i3 < bearerDataLength; i3++) {
                rr.mParcel.writeByte(dis.readByte());
            }
        } catch (IOException ex) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCdmaSms(byte[] pdu, Message result) {
        RILRequest rr = RILRequest.obtain(87, result);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnSim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(64, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnRuim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(97, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        int status2 = translateStatus(status);
        RILRequest rr = RILRequest.obtain(63, response);
        rr.mParcel.writeInt(status2);
        rr.mParcel.writeString(pdu);
        rr.mParcel.writeString(smsc);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToRuim(int status, String pdu, Message response) {
        int status2 = translateStatus(status);
        RILRequest rr = RILRequest.obtain(96, response);
        rr.mParcel.writeInt(status2);
        constructCdmaWriteSmsRilRequest(rr, IccUtils.hexStringToBytes(pdu));
        send(rr);
    }

    private void constructCdmaWriteSmsRilRequest(RILRequest rr, byte[] pdu) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);
        try {
            try {
                int teleServiceId = dis.readInt();
                rr.mParcel.writeInt(teleServiceId);
                byte servicePresent = (byte) dis.readInt();
                rr.mParcel.writeByte(servicePresent);
                int serviceCategory = dis.readInt();
                rr.mParcel.writeInt(serviceCategory);
                int address_digit_mode = dis.readByte();
                rr.mParcel.writeInt(address_digit_mode);
                int address_nbr_mode = dis.readByte();
                rr.mParcel.writeInt(address_nbr_mode);
                int address_ton = dis.readByte();
                rr.mParcel.writeInt(address_ton);
                int address_nbr_plan = dis.readByte();
                rr.mParcel.writeInt(address_nbr_plan);
                int address_nbr_of_digits = dis.readByte();
                rr.mParcel.writeByte((byte) address_nbr_of_digits);
                for (int i = 0; i < address_nbr_of_digits; i++) {
                    rr.mParcel.writeByte(dis.readByte());
                }
                int subaddressType = dis.readByte();
                rr.mParcel.writeInt(subaddressType);
                byte subaddr_odd = dis.readByte();
                rr.mParcel.writeByte(subaddr_odd);
                int subaddr_nbr_of_digits = dis.readByte();
                rr.mParcel.writeByte((byte) subaddr_nbr_of_digits);
                for (int i2 = 0; i2 < subaddr_nbr_of_digits; i2++) {
                    rr.mParcel.writeByte(dis.readByte());
                }
                int bearerDataLength = dis.readByte() & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                rr.mParcel.writeInt(bearerDataLength);
                for (int i3 = 0; i3 < bearerDataLength; i3++) {
                    rr.mParcel.writeByte(dis.readByte());
                }
                riljLog(" teleServiceId=" + teleServiceId + " servicePresent=" + ((int) servicePresent) + " serviceCategory=" + serviceCategory + " address_digit_mode=" + address_digit_mode + " address_nbr_mode=" + address_nbr_mode + " address_ton=" + address_ton + " address_nbr_plan=" + address_nbr_plan + " address_nbr_of_digits=" + address_nbr_of_digits + " subaddressType=" + subaddressType + " subaddr_odd= " + ((int) subaddr_odd) + " subaddr_nbr_of_digits=" + subaddr_nbr_of_digits + " bearerDataLength=" + bearerDataLength);
                if (bais != null) {
                    try {
                    } catch (IOException e) {
                        return;
                    }
                }
            } catch (IOException ex) {
                riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
                if (bais != null) {
                    try {
                        bais.close();
                    } catch (IOException e2) {
                        riljLog("sendSmsCdma: close input stream exception" + e2);
                        return;
                    }
                }
                if (dis != null) {
                    dis.close();
                }
            }
        } finally {
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e3) {
                    riljLog("sendSmsCdma: close input stream exception" + e3);
                }
            }
            if (dis != null) {
                dis.close();
            }
        }
    }

    private int translateStatus(int status) {
        switch (status & 7) {
            case 1:
            case 2:
            case 4:
            case 6:
            default:
                return 1;
            case 3:
                return 0;
            case 5:
                return 3;
            case 7:
                return 2;
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        RILRequest rr = RILRequest.obtain(27, result);
        rr.mParcel.writeInt(7);
        rr.mParcel.writeString(radioTechnology);
        rr.mParcel.writeString(profile);
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(user);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(authType);
        rr.mParcel.writeString(protocol);
        String log_apn = apn;
        String log_user = user;
        String log_password = password;
        if (!"eng".equals(Build.TYPE)) {
            if (log_apn != null) {
                log_apn = "*****";
            }
            if (log_user != null) {
                log_user = "*****";
            }
            if (log_password != null) {
                log_password = "*****";
            }
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + radioTechnology + " " + profile + " " + log_apn + " " + log_user + " " + log_password + " " + authType + " " + protocol);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deactivateDataCall(int cid, int reason, Message result) {
        RILRequest rr = RILRequest.obtain(41, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(Integer.toString(cid));
        rr.mParcel.writeString(Integer.toString(reason));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cid + " " + reason);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setRadioPower(boolean on, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(23, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + (on ? " on" : " off"));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void requestShutdown(Message result) {
        RILRequest rr = RILRequest.obtain(129, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSuppServiceNotifications(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(62, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(37, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(success ? 1 : 0);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(88, result);
        rr.mParcel.writeInt(success ? 0 : 1);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        RILRequest rr = RILRequest.obtain(106, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(success ? "1" : "0");
        rr.mParcel.writeString(ackPdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + success + " [" + ackPdu + ']');
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message result) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(28, result);
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(fileid);
        rr.mParcel.writeString(path);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(pin2);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> iccIO: " + requestToString(rr.mRequest) + " 0x" + Integer.toHexString(command) + " 0x" + Integer.toHexString(fileid) + "  path: " + path + "," + p1 + "," + p2 + "," + p3 + " aid: " + aid);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCLIR(Message result) {
        RILRequest rr = RILRequest.obtain(31, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCLIR(int clirMode, Message result) {
        RILRequest rr = RILRequest.obtain(32, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(clirMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + clirMode);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallWaiting(int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(35, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + serviceClass);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(36, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(enable ? 1 : 0);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enable + ", " + serviceClass);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeAutomatic(Message response) {
        RILRequest rr = RILRequest.obtain(46, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter mainLogWriter = new RILRadioLogWriter(2, 0);
            mainLogWriter.setStackTrace();
            Thread mainLogThread = new Thread(mainLogWriter);
            if (mainLogThread != null) {
                mainLogThread.start();
            }
            RILRadioLogWriter radioLogWriter = new RILRadioLogWriter(2, 2);
            radioLogWriter.setLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            Thread radioLogThread = new Thread(radioLogWriter);
            if (radioLogThread != null) {
                radioLogThread.start();
            }
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr = RILRequest.obtain(47, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);
        rr.mParcel.writeString(operatorNumeric);
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter mainLogWriter = new RILRadioLogWriter(2, 0);
            mainLogWriter.setStackTrace();
            Thread mainLogThread = new Thread(mainLogWriter);
            if (mainLogThread != null) {
                mainLogThread.start();
            }
            RILRadioLogWriter radioLogWriter = new RILRadioLogWriter(2, 2);
            radioLogWriter.setLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);
            Thread radioLogThread = new Thread(radioLogWriter);
            if (radioLogThread != null) {
                radioLogThread.start();
            }
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getNetworkSelectionMode(Message response) {
        RILRequest rr = RILRequest.obtain(45, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getAvailableNetworks(Message response) {
        RILRequest rr = RILRequest.obtain(48, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        if (TelBrand.IS_DCM) {
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(4, rr), SEARCH_NETWORK_DELAY_TIME);
            return;
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        RILRequest rr = RILRequest.obtain(34, response);
        rr.mParcel.writeInt(action);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(timeSeconds);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + action + " " + cfReason + " " + serviceClass + timeSeconds);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        RILRequest rr = RILRequest.obtain(33, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cfReason + " " + serviceClass);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCLIP(Message response) {
        RILRequest rr = RILRequest.obtain(55, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getBasebandVersion(Message response) {
        RILRequest rr = RILRequest.obtain(51, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(42, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " [" + facility + " " + serviceClass + " " + appId + "]");
        rr.mParcel.writeInt(4);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(43, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " [" + facility + " " + lockState + " " + serviceClass + " " + appId + "]");
        rr.mParcel.writeInt(5);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(lockState ? "1" : "0");
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendUSSD(String ussdString, Message response) {
        RILRequest rr = RILRequest.obtain(29, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " *******");
        rr.mParcel.writeString(ussdString);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void cancelPendingUssd(Message response) {
        RILRequest rr = RILRequest.obtain(30, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void resetRadio(Message result) {
        RILRequest rr = RILRequest.obtain(58, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setLocalCallHold(int lchStatus) {
        Rlog.d(RILJ_LOG_TAG, "setLocalCallHold: lchStatus is " + lchStatus);
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_SET_LOCAL_CALL_HOLD, 1, new byte[]{(byte) (lchStatus & 127)}, null);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getModemCapability(Message response) {
        Rlog.d(RILJ_LOG_TAG, "GetModemCapability");
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_GET_MODEM_CAPABILITY, 0, null, response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void updateStackBinding(int stack, int enable, Message response) {
        Rlog.d(RILJ_LOG_TAG, "UpdateStackBinding: on Stack: " + stack + ", enable/disable: " + enable);
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_UPDATE_SUB_BINDING, 2, new byte[]{(byte) stack, (byte) enable}, response);
    }

    private void sendOemRilRequestRaw(int requestId, int numPayload, byte[] payload, Message response) {
        byte[] request = new byte[this.mHeaderSize + (numPayload * 1)];
        ByteBuffer buf = ByteBuffer.wrap(request);
        buf.order(ByteOrder.nativeOrder());
        buf.put(OEM_IDENTIFIER.getBytes());
        buf.putInt(requestId);
        if (numPayload > 0 && payload != null) {
            buf.putInt(numPayload * 1);
            for (byte b : payload) {
                buf.put(b);
            }
        }
        invokeOemRilRequestRaw(request, response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        if (!TelBrand.IS_SBM || !replaceInvokeOemRilRequestRaw(data, response)) {
            RILRequest rr = RILRequest.obtain(59, response);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + "[" + IccUtils.bytesToHexString(data) + "]");
            rr.mParcel.writeByteArray(data);
            send(rr);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        RILRequest rr = RILRequest.obtain(60, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeStringArray(strings);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setBandMode(int bandMode, Message response) {
        RILRequest rr = RILRequest.obtain(65, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(bandMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + bandMode);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryAvailableBandMode(Message response) {
        RILRequest rr = RILRequest.obtain(66, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendTerminalResponse(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(70, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelope(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(69, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelopeWithStatus(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(107, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + '[' + contents + ']');
        rr.mParcel.writeString(contents);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(71, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        int[] param = new int[1];
        if (!accept) {
            i = 0;
        }
        param[0] = i;
        rr.mParcel.writeIntArray(param);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredNetworkType(int networkType, Message response) {
        RILRequest rr = RILRequest.obtain(73, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(networkType);
        this.mPreferredNetworkType = networkType;
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + networkType);
        if (TelBrand.IS_DCM && LOG_NW) {
            RILRadioLogWriter r = new RILRadioLogWriter(0, 0);
            r.setStackTrace();
            Thread thread = new Thread(r);
            if (thread != null) {
                thread.start();
            }
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredNetworkType(Message response) {
        RILRequest rr = RILRequest.obtain(74, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getNeighboringCids(Message response) {
        RILRequest rr = RILRequest.obtain(75, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setLocationUpdates(boolean enable, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(76, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + enable);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSmscAddress(Message result) {
        RILRequest rr = RILRequest.obtain(100, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSmscAddress(String address, Message result) {
        RILRequest rr = RILRequest.obtain(101, result);
        rr.mParcel.writeString(address);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + address);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportSmsMemoryStatus(boolean available, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(102, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!available) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + available);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportStkServiceIsRunning(Message result) {
        RILRequest rr = RILRequest.obtain(103, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getGsmBroadcastConfig(Message response) {
        RILRequest rr = RILRequest.obtain(89, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        RILRequest rr = RILRequest.obtain(90, response);
        int numOfConfig = config.length;
        rr.mParcel.writeInt(numOfConfig);
        for (int i = 0; i < numOfConfig; i++) {
            rr.mParcel.writeInt(config[i].getFromServiceId());
            rr.mParcel.writeInt(config[i].getToServiceId());
            rr.mParcel.writeInt(config[i].getFromCodeScheme());
            rr.mParcel.writeInt(config[i].getToCodeScheme());
            rr.mParcel.writeInt(config[i].isSelected() ? 1 : 0);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + numOfConfig + " configs : ");
        for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : config) {
            riljLog(smsBroadcastConfigInfo.toString());
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(91, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateScreenState() {
        int oldState = this.mDefaultDisplayState;
        this.mDefaultDisplayState = this.mDefaultDisplay.getState();
        if (this.mDefaultDisplayState == oldState) {
            return;
        }
        if (oldState != 2 && this.mDefaultDisplayState == 2) {
            sendScreenState(true);
        } else if ((oldState == 2 || oldState == 0) && this.mDefaultDisplayState != 2) {
            sendScreenState(false);
        }
    }

    private void sendScreenState(boolean on) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(61, null);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + on);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands
    protected void onRadioAvailable() {
        updateScreenState();
    }

    private CommandsInterface.RadioState getRadioStateFromInt(int stateInt) {
        switch (stateInt) {
            case 0:
                return CommandsInterface.RadioState.RADIO_OFF;
            case 1:
                return CommandsInterface.RadioState.RADIO_UNAVAILABLE;
            case 10:
                return CommandsInterface.RadioState.RADIO_ON;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateInt);
        }
    }

    private void switchToRadioState(CommandsInterface.RadioState newState) {
        setRadioState(newState);
    }

    private void acquireWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLock.acquire();
            this.mWakeLockCount++;
            this.mSender.removeMessages(2);
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(2), this.mWakeLockTimeout);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void decrementWakeLock() {
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount > 1) {
                this.mWakeLockCount--;
            } else {
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean clearWakeLock() {
        boolean z = false;
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount != 0 || this.mWakeLock.isHeld()) {
                Rlog.d(RILJ_LOG_TAG, "NOTE: mWakeLockCount is " + this.mWakeLockCount + "at time of clearing");
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
                z = true;
            }
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void send(RILRequest rr) {
        if (this.mSocket == null) {
            rr.onError(1, null);
            rr.release();
            return;
        }
        Message msg = this.mSender.obtainMessage(1, rr);
        acquireWakeLock();
        msg.sendToTarget();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processResponse(Parcel p) {
        RILRequest rr;
        int type = p.readInt();
        if (type == 1) {
            processUnsolicited(p);
        } else if (type == 0 && (rr = processSolicited(p)) != null) {
            rr.release();
            decrementWakeLock();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearRequestList(int error, boolean loggable) {
        synchronized (this.mRequestList) {
            int count = this.mRequestList.size();
            if (loggable) {
                Rlog.d(RILJ_LOG_TAG, "clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + count);
            }
            for (int i = 0; i < count; i++) {
                RILRequest rr = this.mRequestList.valueAt(i);
                if (loggable) {
                    Rlog.d(RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + requestToString(rr.mRequest));
                }
                rr.onError(error, null);
                rr.release();
                decrementWakeLock();
            }
            this.mRequestList.clear();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = this.mRequestList.get(serial);
            if (rr != null) {
                this.mRequestList.remove(serial);
            }
        }
        return rr;
    }

    private RILRequest processSolicited(Parcel p) {
        Thread thread;
        Thread thread2;
        int serial = p.readInt();
        int error = p.readInt();
        RILRequest rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: " + serial + " error: " + error);
            return null;
        }
        Object ret = null;
        if (error == 0 || p.dataAvail() > 0) {
            try {
                switch (rr.mRequest) {
                    case 1:
                        ret = responseIccCardStatus(p);
                        break;
                    case 2:
                        ret = responseInts(p);
                        break;
                    case 3:
                        ret = responseInts(p);
                        break;
                    case 4:
                        ret = responseInts(p);
                        break;
                    case 5:
                        ret = responseInts(p);
                        break;
                    case 6:
                        ret = responseInts(p);
                        break;
                    case 7:
                        ret = responseInts(p);
                        break;
                    case 8:
                        ret = responseInts(p);
                        break;
                    case 9:
                        ret = responseCallList(p);
                        break;
                    case 10:
                        ret = responseVoid(p);
                        break;
                    case 11:
                        ret = responseString(p);
                        break;
                    case 12:
                        ret = responseVoid(p);
                        break;
                    case 13:
                        ret = responseVoid(p);
                        break;
                    case 14:
                        if (this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
                            riljLog("testing emergency call, notify ECM Registrants");
                            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                        }
                        ret = responseVoid(p);
                        break;
                    case 15:
                        ret = responseVoid(p);
                        break;
                    case 16:
                        ret = responseVoid(p);
                        break;
                    case 17:
                        ret = responseVoid(p);
                        break;
                    case 18:
                        ret = responseInts(p);
                        break;
                    case 19:
                        ret = responseSignalStrength(p);
                        break;
                    case 20:
                        ret = responseStrings(p);
                        break;
                    case 21:
                        ret = responseStrings(p);
                        break;
                    case 22:
                        ret = responseStrings(p);
                        break;
                    case 23:
                        ret = responseVoid(p);
                        break;
                    case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                        ret = responseVoid(p);
                        break;
                    case 25:
                        ret = responseSMS(p);
                        break;
                    case 26:
                        ret = responseSMS(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /* 27 */:
                        ret = responseSetupDataCall(p);
                        break;
                    case 28:
                        ret = responseICC_IO(p);
                        break;
                    case IccRecords.EVENT_REFRESH_OEM /* 29 */:
                        ret = responseVoid(p);
                        break;
                    case 30:
                        ret = responseVoid(p);
                        break;
                    case 31:
                        ret = responseInts(p);
                        break;
                    case 32:
                        ret = responseVoid(p);
                        break;
                    case 33:
                        ret = responseCallForward(p);
                        break;
                    case 34:
                        ret = responseVoid(p);
                        break;
                    case 35:
                        ret = responseInts(p);
                        break;
                    case 36:
                        ret = responseVoid(p);
                        break;
                    case 37:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /* 38 */:
                        ret = responseString(p);
                        break;
                    case 39:
                        ret = responseString(p);
                        break;
                    case 40:
                        ret = responseVoid(p);
                        break;
                    case 41:
                        ret = responseVoid(p);
                        break;
                    case 42:
                        ret = responseInts(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /* 43 */:
                        ret = responseInts(p);
                        break;
                    case CallFailCause.CHANNEL_NOT_AVAIL /* 44 */:
                        ret = responseVoid(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /* 45 */:
                        ret = responseInts(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
                        ret = responseVoid(p);
                        break;
                    case 47:
                        ret = responseVoid(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /* 48 */:
                        ret = responseOperatorInfos(p);
                        break;
                    case 49:
                        ret = responseVoid(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /* 50 */:
                        ret = responseVoid(p);
                        break;
                    case 51:
                        ret = responseString(p);
                        break;
                    case 52:
                        ret = responseVoid(p);
                        break;
                    case 53:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_SO68 /* 54 */:
                        ret = responseInts(p);
                        break;
                    case 55:
                        ret = responseInts(p);
                        break;
                    case 56:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_1X_ADVANCED_ENABLED /* 57 */:
                        ret = responseDataCallList(p);
                        break;
                    case 58:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /* 59 */:
                        ret = responseRaw(p);
                        break;
                    case 60:
                        ret = responseStrings(p);
                        break;
                    case 61:
                        ret = responseVoid(p);
                        break;
                    case 62:
                        ret = responseVoid(p);
                        break;
                    case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /* 63 */:
                        ret = responseInts(p);
                        break;
                    case 64:
                        ret = responseVoid(p);
                        break;
                    case 65:
                        ret = responseVoid(p);
                        break;
                    case 66:
                        ret = responseInts(p);
                        break;
                    case 67:
                        ret = responseString(p);
                        break;
                    case 68:
                        ret = responseVoid(p);
                        break;
                    case 69:
                        ret = responseString(p);
                        break;
                    case 70:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /* 71 */:
                        ret = responseInts(p);
                        break;
                    case 72:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /* 73 */:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_25 /* 74 */:
                        ret = responseGetPreferredNetworkType(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_26 /* 75 */:
                        ret = responseCellList(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /* 76 */:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /* 77 */:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_26 /* 78 */:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /* 79 */:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /* 80 */:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BSR_TIMER /* 81 */:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /* 82 */:
                        ret = responseVoid(p);
                        break;
                    case 83:
                        ret = responseInts(p);
                        break;
                    case 84:
                        ret = responseVoid(p);
                        break;
                    case 85:
                        ret = responseVoid(p);
                        break;
                    case 86:
                        ret = responseVoid(p);
                        break;
                    case 87:
                        ret = responseSMS(p);
                        break;
                    case 88:
                        ret = responseVoid(p);
                        break;
                    case 89:
                        ret = responseGmsBroadcastConfig(p);
                        break;
                    case 90:
                        ret = responseVoid(p);
                        break;
                    case 91:
                        ret = responseVoid(p);
                        break;
                    case 92:
                        ret = responseCdmaBroadcastConfig(p);
                        break;
                    case 93:
                        ret = responseVoid(p);
                        break;
                    case 94:
                        ret = responseVoid(p);
                        break;
                    case 95:
                        ret = responseStrings(p);
                        break;
                    case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /* 96 */:
                        ret = responseInts(p);
                        break;
                    case 97:
                        ret = responseVoid(p);
                        break;
                    case 98:
                        ret = responseStrings(p);
                        break;
                    case 99:
                        ret = responseVoid(p);
                        break;
                    case IccRecords.EVENT_GET_ICC_RECORD_DONE /* 100 */:
                        ret = responseString(p);
                        break;
                    case 101:
                        ret = responseVoid(p);
                        break;
                    case 102:
                        ret = responseVoid(p);
                        break;
                    case 103:
                        ret = responseVoid(p);
                        break;
                    case 104:
                        ret = responseInts(p);
                        break;
                    case 105:
                        ret = responseString(p);
                        break;
                    case 106:
                        ret = responseVoid(p);
                        break;
                    case 107:
                        ret = responseICC_IO(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I /* 108 */:
                        ret = responseInts(p);
                        break;
                    case 109:
                        ret = responseCellInfoList(p);
                        break;
                    case 110:
                        ret = responseVoid(p);
                        break;
                    case 111:
                        ret = responseVoid(p);
                        break;
                    case 112:
                        ret = responseInts(p);
                        break;
                    case 113:
                        ret = responseSMS(p);
                        break;
                    case 114:
                        ret = responseICC_IO(p);
                        break;
                    case 115:
                        ret = responseInts(p);
                        break;
                    case 116:
                        ret = responseVoid(p);
                        break;
                    case 117:
                        ret = responseICC_IO(p);
                        break;
                    case 118:
                        ret = responseString(p);
                        break;
                    case 119:
                        ret = responseVoid(p);
                        break;
                    case 120:
                        ret = responseVoid(p);
                        break;
                    case 121:
                        ret = responseVoid(p);
                        break;
                    case 122:
                        ret = responseVoid(p);
                        break;
                    case 123:
                        ret = responseVoid(p);
                        break;
                    case 124:
                        ret = responseHardwareConfig(p);
                        break;
                    case 125:
                        ret = responseICC_IOBase64(p);
                        break;
                    case OemCdmaTelephonyManager.OEM_RIL_CDMA_NAM_Info.SIZE /* 126 */:
                    case 127:
                    case 130:
                    case 131:
                    default:
                        throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
                    case 128:
                        ret = responseVoid(p);
                        break;
                    case 129:
                        ret = responseVoid(p);
                        break;
                    case 132:
                        ret = responseGetDataCallProfile(p);
                        break;
                    case 133:
                        ret = responseString(p);
                        break;
                    case 134:
                        ret = responseICC_IO(p);
                        break;
                    case 135:
                        ret = responseInts(p);
                        break;
                    case 136:
                        ret = responseVoid(p);
                        break;
                    case 137:
                        ret = responseICC_IO(p);
                        break;
                }
            } catch (Throwable tr) {
                Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< " + requestToString(rr.mRequest) + " exception, possible invalid RIL response", tr);
                if (rr.mResult == null) {
                    return rr;
                }
                AsyncResult.forMessage(rr.mResult, (Object) null, tr);
                rr.mResult.sendToTarget();
                return rr;
            }
        }
        if (rr.mRequest == 129) {
            riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " + error + " Setting Radio State to Unavailable regardless of error.");
            setRadioState(CommandsInterface.RadioState.RADIO_UNAVAILABLE);
        }
        switch (rr.mRequest) {
            case 3:
            case 5:
                if (this.mIccStatusChangedRegistrants != null) {
                    riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
            case 47:
                if (TelBrand.IS_DCM && LOG_NS) {
                    RILRadioLogWriter radioLogWriter = new RILRadioLogWriter(2, 2);
                    radioLogWriter.setLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " error: " + CommandException.fromRilErrno(error));
                    Thread thread3 = new Thread(radioLogWriter);
                    if (thread3 != null) {
                        thread3.start();
                        break;
                    }
                }
                break;
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /* 73 */:
                if (TelBrand.IS_DCM && LOG_NW && (thread = new Thread(new RILRadioLogWriter(0, 1))) != null) {
                    thread.start();
                    break;
                }
                break;
        }
        if (error != 0) {
            switch (rr.mRequest) {
                case 2:
                case 4:
                case 6:
                case 7:
                case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /* 43 */:
                    if (this.mIccStatusChangedRegistrants != null) {
                        riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                        this.mIccStatusChangedRegistrants.notifyRegistrants();
                        break;
                    }
                    break;
                case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
                case 47:
                    if (TelBrand.IS_DCM && LOG_NS) {
                        RILRadioLogWriter radioLogWriter2 = new RILRadioLogWriter(2, 2);
                        radioLogWriter2.setLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " error: " + CommandException.fromRilErrno(error));
                        Thread thread4 = new Thread(radioLogWriter2);
                        if (thread4 != null) {
                            thread4.start();
                            break;
                        }
                    }
                    break;
                case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /* 73 */:
                    if (TelBrand.IS_DCM && LOG_NW && (thread2 = new Thread(new RILRadioLogWriter(0, 1))) != null) {
                        thread2.start();
                        break;
                    }
                    break;
            }
            rr.onError(error, ret);
            return rr;
        }
        riljLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " " + retToString(rr.mRequest, ret));
        if (rr.mResult == null) {
            return rr;
        }
        AsyncResult.forMessage(rr.mResult, ret, (Throwable) null);
        rr.mResult.sendToTarget();
        return rr;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String retToString(int req, Object ret) {
        if (ret == null) {
            return "";
        }
        switch (req) {
            case 11:
            case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /* 38 */:
            case 39:
            case 115:
            case 117:
                return "";
            default:
                if (ret instanceof int[]) {
                    Object intArray = (int[]) ret;
                    int length = intArray.length;
                    StringBuilder sb = new StringBuilder("{");
                    if (length > 0) {
                        sb.append(intArray[0]);
                        for (int i = 0 + 1; i < length; i++) {
                            sb.append(", ").append(intArray[i]);
                        }
                    }
                    sb.append("}");
                    return sb.toString();
                } else if (ret instanceof String[]) {
                    Object strings = (String[]) ret;
                    int length2 = strings.length;
                    StringBuilder sb2 = new StringBuilder("{");
                    if (length2 > 0) {
                        sb2.append(strings[0]);
                        for (int i2 = 0 + 1; i2 < length2; i2++) {
                            sb2.append(", ").append(strings[i2]);
                        }
                    }
                    sb2.append("}");
                    return sb2.toString();
                } else if (req == 9) {
                    StringBuilder sb3 = new StringBuilder(" ");
                    Iterator i$ = ((ArrayList) ret).iterator();
                    while (i$.hasNext()) {
                        sb3.append("[").append(i$.next()).append("] ");
                    }
                    return sb3.toString();
                } else if (req == 75) {
                    StringBuilder sb4 = new StringBuilder(" ");
                    Iterator i$2 = ((ArrayList) ret).iterator();
                    while (i$2.hasNext()) {
                        sb4.append(i$2.next()).append(" ");
                    }
                    return sb4.toString();
                } else if (req != 124) {
                    return ret.toString();
                } else {
                    StringBuilder sb5 = new StringBuilder(" ");
                    Iterator i$3 = ((ArrayList) ret).iterator();
                    while (i$3.hasNext()) {
                        sb5.append("[").append(i$3.next()).append("] ");
                    }
                    return sb5.toString();
                }
        }
    }

    private void processUnsolicited(Parcel p) {
        Object ret;
        int response = p.readInt();
        try {
            switch (response) {
                case 1000:
                    ret = responseVoid(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /* 1001 */:
                    ret = responseVoid(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /* 1002 */:
                    ret = responseVoid(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /* 1003 */:
                    ret = responseString(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /* 1004 */:
                    ret = responseString(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /* 1005 */:
                    ret = responseInts(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /* 1006 */:
                    ret = responseStrings(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_PREEMPTED /* 1007 */:
                case 1041:
                case 1042:
                default:
                    throw new RuntimeException("Unrecognized unsol response: " + response);
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /* 1008 */:
                    ret = responseString(p);
                    break;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /* 1009 */:
                    ret = responseSignalStrength(p);
                    break;
                case 1010:
                    ret = responseDataCallList(p);
                    break;
                case 1011:
                    ret = responseSuppServiceNotification(p);
                    break;
                case 1012:
                    ret = responseVoid(p);
                    break;
                case 1013:
                    ret = responseString(p);
                    break;
                case 1014:
                    ret = responseString(p);
                    break;
                case CharacterSets.UTF_16 /* 1015 */:
                    ret = responseInts(p);
                    break;
                case 1016:
                    ret = responseVoid(p);
                    break;
                case 1017:
                    ret = responseSimRefresh(p);
                    break;
                case 1018:
                    ret = responseCallRing(p);
                    break;
                case 1019:
                    ret = responseVoid(p);
                    break;
                case 1020:
                    ret = responseCdmaSms(p);
                    break;
                case 1021:
                    ret = responseRaw(p);
                    break;
                case 1022:
                    ret = responseVoid(p);
                    break;
                case 1023:
                    ret = responseInts(p);
                    break;
                case 1024:
                    ret = responseVoid(p);
                    break;
                case 1025:
                    ret = responseCdmaCallWaiting(p);
                    break;
                case 1026:
                    ret = responseInts(p);
                    break;
                case 1027:
                    ret = responseCdmaInformationRecord(p);
                    break;
                case 1028:
                    ret = responseRaw(p);
                    break;
                case 1029:
                    ret = responseInts(p);
                    break;
                case 1030:
                    ret = responseVoid(p);
                    break;
                case 1031:
                    ret = responseInts(p);
                    break;
                case 1032:
                    ret = responseInts(p);
                    break;
                case 1033:
                    ret = responseVoid(p);
                    break;
                case 1034:
                    ret = responseInts(p);
                    break;
                case 1035:
                    ret = responseInts(p);
                    break;
                case 1036:
                    ret = responseCellInfoList(p);
                    break;
                case 1037:
                    ret = responseVoid(p);
                    break;
                case 1038:
                    ret = responseInts(p);
                    break;
                case 1039:
                    ret = responseInts(p);
                    break;
                case 1040:
                    ret = responseHardwareConfig(p);
                    break;
                case 1043:
                    ret = responseSsData(p);
                    break;
                case 1044:
                    ret = responseString(p);
                    break;
            }
            switch (response) {
                case 1000:
                    CommandsInterface.RadioState newState = getRadioStateFromInt(p.readInt());
                    unsljLogMore(response, newState.toString());
                    switchToRadioState(newState);
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /* 1001 */:
                    unsljLog(response);
                    this.mCallStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /* 1002 */:
                    unsljLog(response);
                    this.mVoiceNetworkStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /* 1003 */:
                    unsljLog(response);
                    String[] a = new String[2];
                    a[1] = (String) ret;
                    SmsMessage sms = SmsMessage.newFromCMT(a);
                    if (this.mGsmSmsRegistrant != null) {
                        this.mGsmSmsRegistrant.notifyRegistrant(new AsyncResult((Object) null, sms, (Throwable) null));
                        return;
                    }
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /* 1004 */:
                    unsljLogRet(response, ret);
                    if (this.mSmsStatusRegistrant != null) {
                        this.mSmsStatusRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /* 1005 */:
                    unsljLogRet(response, ret);
                    int[] smsIndex = (int[]) ret;
                    if (smsIndex.length != 1) {
                        riljLog(" NEW_SMS_ON_SIM ERROR with wrong length " + smsIndex.length);
                        return;
                    } else if (this.mSmsOnSimRegistrant != null) {
                        this.mSmsOnSimRegistrant.notifyRegistrant(new AsyncResult((Object) null, smsIndex, (Throwable) null));
                        return;
                    } else {
                        return;
                    }
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /* 1006 */:
                    String[] resp = (String[]) ret;
                    if (resp.length < 2) {
                        resp = new String[]{((String[]) ret)[0], null};
                    }
                    unsljLogMore(response, resp[0]);
                    if (this.mUSSDRegistrant != null) {
                        this.mUSSDRegistrant.notifyRegistrant(new AsyncResult((Object) null, resp, (Throwable) null));
                        return;
                    }
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_PREEMPTED /* 1007 */:
                case 1041:
                case 1042:
                default:
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /* 1008 */:
                    unsljLogRet(response, ret);
                    Object[] result = {ret, Long.valueOf(p.readLong())};
                    if (SystemProperties.getBoolean("telephony.test.ignore.nitz", false)) {
                        riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
                        return;
                    }
                    if (this.mNITZTimeRegistrant != null) {
                        this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult((Object) null, result, (Throwable) null));
                    }
                    this.mLastNITZTimeInfo = result;
                    return;
                case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /* 1009 */:
                    if (this.mSignalStrengthRegistrant != null) {
                        this.mSignalStrengthRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1010:
                    unsljLogRet(response, ret);
                    this.mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                    return;
                case 1011:
                    unsljLogRet(response, ret);
                    if (this.mSsnRegistrant != null) {
                        this.mSsnRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1012:
                    unsljLog(response);
                    if (this.mCatSessionEndRegistrant != null) {
                        this.mCatSessionEndRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1013:
                    unsljLog(response);
                    if (this.mCatProCmdRegistrant != null) {
                        this.mCatProCmdRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1014:
                    unsljLog(response);
                    if (this.mCatEventRegistrant != null) {
                        this.mCatEventRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case CharacterSets.UTF_16 /* 1015 */:
                    unsljLogRet(response, ret);
                    if (this.mCatCallSetUpRegistrant != null) {
                        this.mCatCallSetUpRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1016:
                    unsljLog(response);
                    if (this.mIccSmsFullRegistrant != null) {
                        this.mIccSmsFullRegistrant.notifyRegistrant();
                        return;
                    }
                    return;
                case 1017:
                    unsljLogRet(response, ret);
                    if (this.mIccRefreshRegistrants != null) {
                        this.mIccRefreshRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1018:
                    unsljLogRet(response, ret);
                    if (this.mRingRegistrant != null) {
                        this.mRingRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1019:
                    unsljLog(response);
                    if (this.mIccStatusChangedRegistrants != null) {
                        this.mIccStatusChangedRegistrants.notifyRegistrants();
                        return;
                    }
                    return;
                case 1020:
                    unsljLog(response);
                    SmsMessage sms2 = (SmsMessage) ret;
                    if (this.mCdmaSmsRegistrant != null) {
                        this.mCdmaSmsRegistrant.notifyRegistrant(new AsyncResult((Object) null, sms2, (Throwable) null));
                        return;
                    }
                    return;
                case 1021:
                    unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) ret));
                    if (this.mGsmBroadcastSmsRegistrant != null) {
                        this.mGsmBroadcastSmsRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1022:
                    unsljLog(response);
                    if (this.mIccSmsFullRegistrant != null) {
                        this.mIccSmsFullRegistrant.notifyRegistrant();
                        return;
                    }
                    return;
                case 1023:
                    unsljLogvRet(response, ret);
                    if (this.mRestrictedStateRegistrant != null) {
                        this.mRestrictedStateRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1024:
                    unsljLog(response);
                    if (this.mEmergencyCallbackModeRegistrant != null) {
                        this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                        return;
                    }
                    return;
                case 1025:
                    unsljLogRet(response, ret);
                    if (this.mCallWaitingInfoRegistrants != null) {
                        this.mCallWaitingInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1026:
                    unsljLogRet(response, ret);
                    if (this.mOtaProvisionRegistrants != null) {
                        this.mOtaProvisionRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1027:
                    try {
                        Iterator i$ = ((ArrayList) ret).iterator();
                        while (i$.hasNext()) {
                            CdmaInformationRecords rec = i$.next();
                            unsljLogRet(response, rec);
                            notifyRegistrantsCdmaInfoRec(rec);
                        }
                        return;
                    } catch (ClassCastException e) {
                        Rlog.e(RILJ_LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                        return;
                    }
                case 1028:
                    unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) ret));
                    ByteBuffer oemHookResponse = ByteBuffer.wrap((byte[]) ret);
                    oemHookResponse.order(ByteOrder.nativeOrder());
                    if (isQcUnsolOemHookResp(oemHookResponse)) {
                        Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
                        processUnsolOemhookResponse(oemHookResponse);
                        return;
                    } else if (this.mUnsolOemHookRawRegistrant != null) {
                        Rlog.d(RILJ_LOG_TAG, "External OEM message, to be notified");
                        this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    } else {
                        return;
                    }
                case 1029:
                    unsljLogvRet(response, ret);
                    if (this.mRingbackToneRegistrants != null) {
                        this.mRingbackToneRegistrants.notifyRegistrants(new AsyncResult((Object) null, Boolean.valueOf(((int[]) ret)[0] == 1), (Throwable) null));
                        return;
                    }
                    return;
                case 1030:
                    unsljLogRet(response, ret);
                    if (this.mResendIncallMuteRegistrants != null) {
                        this.mResendIncallMuteRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1031:
                    unsljLogRet(response, ret);
                    if (this.mCdmaSubscriptionChangedRegistrants != null) {
                        this.mCdmaSubscriptionChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1032:
                    unsljLogRet(response, ret);
                    if (this.mCdmaPrlChangedRegistrants != null) {
                        this.mCdmaPrlChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1033:
                    unsljLogRet(response, ret);
                    if (this.mExitEmergencyCallbackModeRegistrants != null) {
                        this.mExitEmergencyCallbackModeRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
                        return;
                    }
                    return;
                case 1034:
                    unsljLogRet(response, ret);
                    setRadioPower(false, null);
                    setCdmaSubscriptionSource(this.mCdmaSubscription, null);
                    setCellInfoListRate(Integer.MAX_VALUE, null);
                    notifyRegistrantsRilConnectionChanged(((int[]) ret)[0]);
                    if (!this.mRILConnected_SetInitialAttachApn_OnceSkip) {
                        riljLog("[EXTDBG] RILD crash, call setInitialAttachApn");
                        setInitialAttachApn(this.mSetInitialAttachApn_Apn, this.mSetInitialAttachApn_Protocol, this.mSetInitialAttachApn_AuthType, this.mSetInitialAttachApn_Username, this.mSetInitialAttachApn_Password, null);
                    }
                    if (!this.mRILConnected_UpdateOemDataSettings_OnceSkip) {
                        riljLog("[EXTDBG] RILD crash, call updateOemDataSettings");
                        updateOemDataSettings(this.mUpdateOemDataSettings_MobileData, this.mUpdateOemDataSettings_DataRoaming, true, null);
                    }
                    if (this.mSetLimitationByChameleon) {
                        setLimitationByChameleon(this.mLimitationByChameleon, null);
                        return;
                    }
                    return;
                case 1035:
                    unsljLogRet(response, ret);
                    if (this.mVoiceRadioTechChangedRegistrants != null) {
                        this.mVoiceRadioTechChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1036:
                    unsljLogRet(response, ret);
                    if (this.mRilCellInfoListRegistrants != null) {
                        this.mRilCellInfoListRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1037:
                    unsljLog(response);
                    this.mImsNetworkStateChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
                    return;
                case 1038:
                    unsljLogRet(response, ret);
                    if (this.mSubscriptionStatusRegistrants != null) {
                        this.mSubscriptionStatusRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1039:
                    unsljLogRet(response, ret);
                    if (this.mSrvccStateRegistrants != null) {
                        this.mSrvccStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1040:
                    unsljLogRet(response, ret);
                    if (this.mHardwareConfigChangeRegistrants != null) {
                        this.mHardwareConfigChangeRegistrants.notifyRegistrants(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1043:
                    unsljLogRet(response, ret);
                    if (this.mSsRegistrant != null) {
                        this.mSsRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
                case 1044:
                    unsljLogRet(response, ret);
                    if (this.mCatCcAlphaRegistrant != null) {
                        this.mCatCcAlphaRegistrant.notifyRegistrant(new AsyncResult((Object) null, ret, (Throwable) null));
                        return;
                    }
                    return;
            }
        } catch (Throwable tr) {
            Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + response + "Exception:" + tr.toString());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyRegistrantsRilConnectionChanged(int rilVer) {
        this.mRilVersion = rilVer;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult((Object) null, new Integer(rilVer), (Throwable) null));
        }
    }

    private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {
        if (oemHookResponse.capacity() < this.mHeaderSize) {
            Rlog.d(RILJ_LOG_TAG, "RIL_UNSOL_OEM_HOOK_RAW data size is " + oemHookResponse.capacity());
            return false;
        }
        byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
        oemHookResponse.get(oemIdBytes);
        String oemIdString = new String(oemIdBytes);
        Rlog.d(RILJ_LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + oemIdString);
        if (oemIdString.equals(OEM_IDENTIFIER)) {
            return true;
        }
        return false;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class UnsolOemHookBuffer {
        private byte[] mData;
        private int mRilInstance;

        public UnsolOemHookBuffer(int rilInstance, byte[] data) {
            this.mRilInstance = rilInstance;
            this.mData = data;
        }

        public int getRilInstance() {
            return this.mRilInstance;
        }

        public byte[] getUnsolOemHookBuffer() {
            return this.mData;
        }
    }

    private void processUnsolOemhookResponse(ByteBuffer oemHookResponse) {
        int responseId = oemHookResponse.getInt();
        Rlog.d(RILJ_LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + responseId);
        int responseSize = oemHookResponse.getInt();
        if (responseSize < 0) {
            Rlog.e(RILJ_LOG_TAG, "Response Size is Invalid " + responseSize);
            return;
        }
        byte[] responseData = new byte[responseSize];
        if (oemHookResponse.remaining() == responseSize) {
            oemHookResponse.get(responseData, 0, responseSize);
            switch (responseId) {
                case 525304:
                    notifySimRefresh(responseData);
                    return;
                case 525306:
                    notifyWwanIwlanCoexist(responseData);
                    return;
                case 525308:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = mInstanceId" + this.mInstanceId);
                    notifyModemCap(responseData, this.mInstanceId);
                    return;
                case 592825:
                    if (TelBrand.IS_SBM) {
                        notifyOemSignalStrength(responseData);
                        return;
                    }
                    return;
                case 592826:
                    notifyLteBandInfo(responseData);
                    return;
                case 592827:
                    if (TelBrand.IS_SBM) {
                        notifySpeechCodec(responseData);
                        return;
                    }
                    return;
                default:
                    Rlog.d(RILJ_LOG_TAG, "Response ID " + responseId + " is not served in this process.");
                    return;
            }
        } else {
            Rlog.e(RILJ_LOG_TAG, "Response Size(" + responseSize + ") doesnot match remaining bytes(" + oemHookResponse.remaining() + ") in the buffer. So, don't process further");
        }
    }

    protected void notifyWwanIwlanCoexist(byte[] data) {
        this.mWwanIwlanCoexistenceRegistrants.notifyRegistrants(new AsyncResult((Object) null, data, (Throwable) null));
        Rlog.d(RILJ_LOG_TAG, "WWAN, IWLAN coexistence notified to registrants");
    }

    protected void notifySimRefresh(byte[] data) {
        byte b = 0;
        int len = data.length;
        byte[] userdata = new byte[len + 1];
        System.arraycopy(data, 0, userdata, 0, len);
        if (this.mInstanceId != null) {
            b = (byte) (this.mInstanceId.intValue() & 255);
        }
        userdata[len] = b;
        this.mSimRefreshRegistrants.notifyRegistrants(new AsyncResult((Object) null, userdata, (Throwable) null));
        Rlog.d(RILJ_LOG_TAG, "SIM_REFRESH notified to registrants");
    }

    protected void notifyModemCap(byte[] data, Integer phoneId) {
        this.mModemCapRegistrants.notifyRegistrants(new AsyncResult((Object) null, new UnsolOemHookBuffer(phoneId.intValue(), data), (Throwable) null));
        Rlog.d(RILJ_LOG_TAG, "MODEM_CAPABILITY on phone=" + phoneId + " notified to registrants");
    }

    private Object toIntArrayFromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.nativeOrder());
        int numInts = buf.capacity() / 4;
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = buf.getInt();
        }
        return response;
    }

    protected void notifySpeechCodec(byte[] data) {
        if (TelBrand.IS_SBM && this.mSpeechCodecRegistrant != null) {
            this.mSpeechCodecRegistrant.notifyRegistrant(new AsyncResult((Object) null, toIntArrayFromByteArray(data), (Throwable) null));
        }
    }

    protected void notifyOemSignalStrength(byte[] data) {
        if (this.mOemSignalStrengthRegistrant != null) {
            this.mOemSignalStrengthRegistrant.notifyRegistrant(new AsyncResult((Object) null, toIntArrayFromByteArray(data), (Throwable) null));
        }
    }

    private Object responseInts(Parcel p) {
        int numInts = p.readInt();
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = p.readInt();
        }
        return response;
    }

    private Object responseVoid(Parcel p) {
        return null;
    }

    private Object responseCallForward(Parcel p) {
        int numInfos = p.readInt();
        CallForwardInfo[] infos = new CallForwardInfo[numInfos];
        for (int i = 0; i < numInfos; i++) {
            infos[i] = new CallForwardInfo();
            infos[i].status = p.readInt();
            infos[i].reason = p.readInt();
            infos[i].serviceClass = p.readInt();
            infos[i].toa = p.readInt();
            infos[i].number = p.readString();
            infos[i].timeSeconds = p.readInt();
        }
        return infos;
    }

    private Object responseSuppServiceNotification(Parcel p) {
        SuppServiceNotification notification = new SuppServiceNotification();
        notification.notificationType = p.readInt();
        notification.code = p.readInt();
        notification.index = p.readInt();
        notification.type = p.readInt();
        notification.number = p.readString();
        return notification;
    }

    private Object responseCdmaSms(Parcel p) {
        return SmsMessage.newFromParcel(p);
    }

    private Object responseString(Parcel p) {
        return p.readString();
    }

    private Object responseStrings(Parcel p) {
        return p.readStringArray();
    }

    private Object responseRaw(Parcel p) {
        return p.createByteArray();
    }

    private Object responseSMS(Parcel p) {
        return new SmsResponse(p.readInt(), p.readString(), p.readInt());
    }

    private Object responseICC_IO(Parcel p) {
        return new IccIoResult(p.readInt(), p.readInt(), p.readString());
    }

    private Object responseICC_IOBase64(Parcel p) {
        return new IccIoResult(p.readInt(), p.readInt(), Base64.decode(p.readString(), 0));
    }

    private Object responseIccCardStatus(Parcel p) {
        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();
        int numApplications = p.readInt();
        if (numApplications > 8) {
            numApplications = 8;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];
        for (int i = 0; i < numApplications; i++) {
            IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
            appStatus.app_type = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid = p.readString();
            appStatus.app_label = p.readString();
            appStatus.pin1_replaced = p.readInt();
            appStatus.pin1 = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2 = appStatus.PinStateFromRILInt(p.readInt());
            cardStatus.mApplications[i] = appStatus;
        }
        return cardStatus;
    }

    private Object responseSimRefresh(Parcel p) {
        IccRefreshResponse response = new IccRefreshResponse();
        response.refreshResult = p.readInt();
        response.efId = p.readInt();
        response.aid = p.readString();
        return response;
    }

    private Object responseCallList(Parcel p) {
        boolean z;
        int num = p.readInt();
        ArrayList<DriverCall> response = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            DriverCall dc = new DriverCall();
            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            if (p.readInt() != 0) {
                z = true;
            } else {
                z = false;
            }
            dc.isMpty = z;
            dc.isMT = p.readInt() != 0;
            dc.als = p.readInt();
            dc.isVoice = p.readInt() != 0;
            dc.isVoicePrivacy = p.readInt() != 0;
            dc.number = p.readString();
            dc.numberPresentation = DriverCall.presentationFromCLIP(p.readInt());
            dc.name = p.readString();
            dc.namePresentation = DriverCall.presentationFromCLIP(p.readInt());
            if (p.readInt() == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                dc.uusInfo.setUserData(p.createByteArray());
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d", Integer.valueOf(dc.uusInfo.getType()), Integer.valueOf(dc.uusInfo.getDcs()), Integer.valueOf(dc.uusInfo.getUserData().length)));
                riljLogv("Incoming UUS : data (string)=" + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): " + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);
            response.add(dc);
            if (dc.isVoicePrivacy) {
                this.mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                this.mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }
        Collections.sort(response);
        if (num == 0 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("responseCallList: call ended, testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return response;
    }

    private DataCallResponse getDataCallResponse(Parcel p, int version) {
        DataCallResponse dataCall = new DataCallResponse();
        dataCall.version = version;
        if (version < 5) {
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
        } else {
            dataCall.status = p.readInt();
            dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
            if (dataCall.status != DcFailCause.NONE.getErrorCode() || !TextUtils.isEmpty(dataCall.ifname)) {
                String addresses2 = p.readString();
                if (!TextUtils.isEmpty(addresses2)) {
                    dataCall.addresses = addresses2.split(" ");
                }
                String dnses = p.readString();
                if (!TextUtils.isEmpty(dnses)) {
                    dataCall.dnses = dnses.split(" ");
                }
                String gateways = p.readString();
                if (!TextUtils.isEmpty(gateways)) {
                    dataCall.gateways = gateways.split(" ");
                }
                if (version >= 10) {
                    String pcscf = p.readString();
                    if (!TextUtils.isEmpty(pcscf)) {
                        dataCall.pcscf = pcscf.split(" ");
                    }
                    dataCall.mtu = p.readInt();
                }
            } else {
                throw new RuntimeException("getDataCallResponse, no ifname");
            }
        }
        return dataCall;
    }

    private Object responseDataCallList(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        riljLog("responseDataCallList ver=" + ver + " num=" + num);
        ArrayList<DataCallResponse> response = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            response.add(getDataCallResponse(p, ver));
        }
        return response;
    }

    private Object responseSetupDataCall(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        if (ver < 5) {
            DataCallResponse dataCall = new DataCallResponse();
            dataCall.version = ver;
            dataCall.cid = Integer.parseInt(p.readString());
            dataCall.ifname = p.readString();
            if (TextUtils.isEmpty(dataCall.ifname)) {
                throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response, no ifname");
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            if (num >= 4) {
                String dnses = p.readString();
                riljLog("responseSetupDataCall got dnses=" + dnses);
                if (!TextUtils.isEmpty(dnses)) {
                    dataCall.dnses = dnses.split(" ");
                }
            }
            if (num >= 5) {
                String gateways = p.readString();
                riljLog("responseSetupDataCall got gateways=" + gateways);
                if (!TextUtils.isEmpty(gateways)) {
                    dataCall.gateways = gateways.split(" ");
                }
            }
            if (num >= 6) {
                String pcscf = p.readString();
                riljLog("responseSetupDataCall got pcscf=" + pcscf);
                if (!TextUtils.isEmpty(pcscf)) {
                    dataCall.pcscf = pcscf.split(" ");
                }
            }
            if (num < 7) {
                return dataCall;
            }
            dataCall.mtu = Integer.parseInt(p.readString());
            riljLog("responseSetupDataCall got mtu=" + dataCall.mtu);
            return dataCall;
        } else if (num == 1) {
            return getDataCallResponse(p, ver);
        } else {
            throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response expecting 1 RIL_Data_Call_response_v5 got " + num);
        }
    }

    private Object responseOperatorInfos(Parcel p) {
        String strOperatorLong;
        String[] strings = (String[]) responseStrings(p);
        SpnOverride spnOverride = new SpnOverride();
        if (strings.length % 4 != 0) {
            throw new RuntimeException("RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got " + strings.length + " strings, expected multible of 4");
        }
        ArrayList<OperatorInfo> ret = new ArrayList<>(strings.length / 4);
        for (int i = 0; i < strings.length; i += 4) {
            if (spnOverride.containsCarrier(strings[i + 2])) {
                strOperatorLong = spnOverride.getSpn(strings[i + 2]);
            } else {
                strOperatorLong = strings[i + 0];
            }
            ret.add(new OperatorInfo(strOperatorLong, strings[i + 1], strings[i + 2], strings[i + 3]));
        }
        return ret;
    }

    private Object responseCellList(Parcel p) {
        int num = p.readInt();
        ArrayList<NeighboringCellInfo> response = new ArrayList<>();
        int radioType = ((TelephonyManager) this.mContext.getSystemService("phone")).getDataNetworkType(SubscriptionManager.getSubId(this.mInstanceId.intValue())[0]);
        if (radioType != 0) {
            for (int i = 0; i < num; i++) {
                response.add(new NeighboringCellInfo(p.readInt(), p.readString(), radioType));
            }
        }
        return response;
    }

    private Object responseGetPreferredNetworkType(Parcel p) {
        int[] response = (int[]) responseInts(p);
        if (response.length >= 1) {
            this.mPreferredNetworkType = response[0];
        }
        return response;
    }

    private Object responseGmsBroadcastConfig(Parcel p) {
        boolean selected;
        int num = p.readInt();
        ArrayList<SmsBroadcastConfigInfo> response = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            int fromId = p.readInt();
            int toId = p.readInt();
            int fromScheme = p.readInt();
            int toScheme = p.readInt();
            if (p.readInt() == 1) {
                selected = true;
            } else {
                selected = false;
            }
            response.add(new SmsBroadcastConfigInfo(fromId, toId, fromScheme, toScheme, selected));
        }
        return response;
    }

    private Object responseCdmaBroadcastConfig(Parcel p) {
        int[] response;
        int numServiceCategories = p.readInt();
        if (numServiceCategories == 0) {
            response = new int[94];
            response[0] = 31;
            for (int i = 1; i < 94; i += 3) {
                response[i + 0] = i / 3;
                response[i + 1] = 1;
                response[i + 2] = 0;
            }
        } else {
            int numInts = (numServiceCategories * 3) + 1;
            response = new int[numInts];
            response[0] = numServiceCategories;
            for (int i2 = 1; i2 < numInts; i2++) {
                response[i2] = p.readInt();
            }
        }
        return response;
    }

    private Object responseSignalStrength(Parcel p) {
        return SignalStrength.makeSignalStrengthFromRilParcel(p);
    }

    private ArrayList<CdmaInformationRecords> responseCdmaInformationRecord(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CdmaInformationRecords> response = new ArrayList<>(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add(new CdmaInformationRecords(p));
        }
        return response;
    }

    private Object responseCdmaCallWaiting(Parcel p) {
        CdmaCallWaitingNotification notification = new CdmaCallWaitingNotification();
        notification.number = p.readString();
        notification.numberPresentation = CdmaCallWaitingNotification.presentationFromCLIP(p.readInt());
        notification.name = p.readString();
        notification.namePresentation = notification.numberPresentation;
        notification.isPresent = p.readInt();
        notification.signalType = p.readInt();
        notification.alertPitch = p.readInt();
        notification.signal = p.readInt();
        notification.numberType = p.readInt();
        notification.numberPlan = p.readInt();
        return notification;
    }

    private Object responseCallRing(Parcel p) {
        return new char[]{(char) p.readInt(), (char) p.readInt(), (char) p.readInt(), (char) p.readInt()};
    }

    private ArrayList<ApnSetting> responseGetDataCallProfile(Parcel p) {
        int nProfiles = p.readInt();
        riljLog("# data call profiles:" + nProfiles);
        ArrayList<ApnSetting> response = new ArrayList<>(nProfiles);
        for (int i = 0; i < nProfiles; i++) {
            ApnProfileOmh profile = new ApnProfileOmh(p.readInt(), p.readInt());
            riljLog("responseGetDataCallProfile()" + profile.getProfileId() + ":" + profile.getPriority());
            response.add(profile);
        }
        return response;
    }

    private void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        if (infoRec.record instanceof CdmaInformationRecords.CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if (infoRec.record instanceof CdmaInformationRecords.CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if (infoRec.record instanceof CdmaInformationRecords.CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if (infoRec.record instanceof CdmaInformationRecords.CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if (infoRec.record instanceof CdmaInformationRecords.CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if (infoRec.record instanceof CdmaInformationRecords.CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
            }
        } else if ((infoRec.record instanceof CdmaInformationRecords.CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, infoRec.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult((Object) null, infoRec.record, (Throwable) null));
        }
    }

    private ArrayList<CellInfo> responseCellInfoList(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CellInfo> response = new ArrayList<>(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add((CellInfo) CellInfo.CREATOR.createFromParcel(p));
        }
        return response;
    }

    private Object responseHardwareConfig(Parcel p) {
        HardwareConfig hw;
        int num = p.readInt();
        ArrayList<HardwareConfig> response = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            int type = p.readInt();
            switch (type) {
                case 0:
                    hw = new HardwareConfig(type);
                    hw.assignModem(p.readString(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt());
                    break;
                case 1:
                    hw = new HardwareConfig(type);
                    hw.assignSim(p.readString(), p.readInt(), p.readString());
                    break;
                default:
                    throw new RuntimeException("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + type);
            }
            response.add(hw);
        }
        return response;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String requestToString(int request) {
        switch (request) {
            case 1:
                return "GET_SIM_STATUS";
            case 2:
                return "ENTER_SIM_PIN";
            case 3:
                return "ENTER_SIM_PUK";
            case 4:
                return "ENTER_SIM_PIN2";
            case 5:
                return "ENTER_SIM_PUK2";
            case 6:
                return "CHANGE_SIM_PIN";
            case 7:
                return "CHANGE_SIM_PIN2";
            case 8:
                return "ENTER_DEPERSONALIZATION_CODE";
            case 9:
                return "GET_CURRENT_CALLS";
            case 10:
                return "DIAL";
            case 11:
                return "GET_IMSI";
            case 12:
                return "HANGUP";
            case 13:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case 14:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case 15:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case 16:
                return "CONFERENCE";
            case 17:
                return "UDUB";
            case 18:
                return "LAST_CALL_FAIL_CAUSE";
            case 19:
                return "SIGNAL_STRENGTH";
            case 20:
                return "VOICE_REGISTRATION_STATE";
            case 21:
                return "DATA_REGISTRATION_STATE";
            case 22:
                return "OPERATOR";
            case 23:
                return "RADIO_POWER";
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /* 27 */:
                return "SETUP_DATA_CALL";
            case 28:
                return "SIM_IO";
            case IccRecords.EVENT_REFRESH_OEM /* 29 */:
                return "SEND_USSD";
            case 30:
                return "CANCEL_USSD";
            case 31:
                return "GET_CLIR";
            case 32:
                return "SET_CLIR";
            case 33:
                return "QUERY_CALL_FORWARD_STATUS";
            case 34:
                return "SET_CALL_FORWARD";
            case 35:
                return "QUERY_CALL_WAITING";
            case 36:
                return "SET_CALL_WAITING";
            case 37:
                return "SMS_ACKNOWLEDGE";
            case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /* 38 */:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case 40:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /* 43 */:
                return "SET_FACILITY_LOCK";
            case CallFailCause.CHANNEL_NOT_AVAIL /* 44 */:
                return "CHANGE_BARRING_PASSWORD";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /* 45 */:
                return "QUERY_NETWORK_SELECTION_MODE";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /* 48 */:
                return "QUERY_AVAILABLE_NETWORKS ";
            case 49:
                return "DTMF_START";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /* 50 */:
                return "DTMF_STOP";
            case 51:
                return "BASEBAND_VERSION";
            case 52:
                return "SEPARATE_CONNECTION";
            case 53:
                return "SET_MUTE";
            case RadioNVItems.RIL_NV_CDMA_SO68 /* 54 */:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case 56:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case RadioNVItems.RIL_NV_CDMA_1X_ADVANCED_ENABLED /* 57 */:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /* 59 */:
                return "OEM_HOOK_RAW";
            case 60:
                return "OEM_HOOK_STRINGS";
            case 61:
                return "SCREEN_STATE";
            case 62:
                return "SET_SUPP_SVC_NOTIFICATION";
            case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /* 63 */:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case 65:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case 68:
                return "REQUEST_STK_SET_PROFILE";
            case 69:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case 70:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /* 71 */:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case 72:
                return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /* 73 */:
                return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_25 /* 74 */:
                return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_26 /* 75 */:
                return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /* 76 */:
                return "REQUEST_SET_LOCATION_UPDATES";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /* 77 */:
                return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_26 /* 78 */:
                return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /* 79 */:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /* 80 */:
                return "RIL_REQUEST_SET_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_TIMER /* 81 */:
                return "RIL_REQUEST_QUERY_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /* 82 */:
                return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case 83:
                return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case 84:
                return "RIL_REQUEST_CDMA_FLASH";
            case 85:
                return "RIL_REQUEST_CDMA_BURST_DTMF";
            case 86:
                return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case 87:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case 88:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case 91:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case 95:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /* 96 */:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case 97:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case 98:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case 99:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case IccRecords.EVENT_GET_ICC_RECORD_DONE /* 100 */:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case 101:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case 102:
                return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case 103:
                return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case 104:
                return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case 105:
                return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case 106:
                return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case 107:
                return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I /* 108 */:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case 110:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case 111:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case 112:
                return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case 113:
                return "RIL_REQUEST_IMS_SEND_SMS";
            case 114:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case 115:
                return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case 116:
                return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case 117:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case 118:
                return "RIL_REQUEST_NV_READ_ITEM";
            case 119:
                return "RIL_REQUEST_NV_WRITE_ITEM";
            case 120:
                return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case 121:
                return "RIL_REQUEST_NV_RESET_CONFIG";
            case 122:
                return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case 123:
                return "RIL_REQUEST_ALLOW_DATA";
            case 124:
                return "GET_HARDWARE_CONFIG";
            case 125:
                return "RIL_REQUEST_SIM_AUTHENTICATION";
            case OemCdmaTelephonyManager.OEM_RIL_CDMA_NAM_Info.SIZE /* 126 */:
            case 127:
            case 130:
            case 131:
            default:
                return "<unknown request>";
            case 128:
                return "RIL_REQUEST_SET_DATA_PROFILE";
            case 129:
                return "RIL_REQUEST_SHUTDOWN";
            case 132:
                return "RIL_REQUEST_GET_DATA_CALL_PROFILE";
            case 133:
                return "SIM_GET_ATR";
            case 134:
                return "SIM_TRANSMIT_BASIC_SHARP";
            case 135:
                return "SIM_OPEN_CHANNEL_SHARP";
            case 136:
                return "SIM_CLOSE_CHANNEL_SHARP";
            case 137:
                return "SIM_TRANSMIT_CHANNEL_SHARP";
        }
    }

    static String responseToString(int request) {
        switch (request) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /* 1001 */:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /* 1002 */:
                return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /* 1003 */:
                return "UNSOL_RESPONSE_NEW_SMS";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /* 1004 */:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /* 1005 */:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /* 1006 */:
                return "UNSOL_ON_USSD";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_PREEMPTED /* 1007 */:
                return "UNSOL_ON_USSD_REQUEST";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /* 1008 */:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /* 1009 */:
                return "UNSOL_SIGNAL_STRENGTH";
            case 1010:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case 1011:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case 1012:
                return "UNSOL_STK_SESSION_END";
            case 1013:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case 1014:
                return "UNSOL_STK_EVENT_NOTIFY";
            case CharacterSets.UTF_16 /* 1015 */:
                return "UNSOL_STK_CALL_SETUP";
            case 1016:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case 1017:
                return "UNSOL_SIM_REFRESH";
            case 1018:
                return "UNSOL_CALL_RING";
            case 1019:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case 1020:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case 1021:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case 1022:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case 1023:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case 1024:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case 1025:
                return "UNSOL_CDMA_CALL_WAITING";
            case 1026:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case 1027:
                return "UNSOL_CDMA_INFO_REC";
            case 1028:
                return "UNSOL_OEM_HOOK_RAW";
            case 1029:
                return "UNSOL_RINGBACK_TONE";
            case 1030:
                return "UNSOL_RESEND_INCALL_MUTE";
            case 1031:
                return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case 1032:
                return "UNSOL_CDMA_PRL_CHANGED";
            case 1033:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case 1034:
                return "UNSOL_RIL_CONNECTED";
            case 1035:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case 1036:
                return "UNSOL_CELL_INFO_LIST";
            case 1037:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case 1038:
                return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case 1039:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case 1040:
                return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case 1041:
            case 1042:
            default:
                return "<unknown response>";
            case 1043:
                return "UNSOL_ON_SS";
            case 1044:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
        }
    }

    private void riljLog(String msg) {
        Rlog.d(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void riljLogv(String msg) {
        Rlog.v(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void unsljLog(int response) {
        riljLog("[UNSL]< " + responseToString(response));
    }

    private void unsljLogMore(int response, String more) {
        riljLog("[UNSL]< " + responseToString(response) + " " + more);
    }

    private void unsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    private void unsljLogvRet(int response, Object ret) {
        riljLogv("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    private Object responseSsData(Parcel p) {
        SsData ssData = new SsData();
        ssData.serviceType = ssData.ServiceTypeFromRILInt(p.readInt());
        ssData.requestType = ssData.RequestTypeFromRILInt(p.readInt());
        ssData.teleserviceType = ssData.TeleserviceTypeFromRILInt(p.readInt());
        ssData.serviceClass = p.readInt();
        ssData.result = p.readInt();
        int num = p.readInt();
        if (!ssData.serviceType.isTypeCF() || !ssData.requestType.isTypeInterrogation()) {
            ssData.ssInfo = new int[num];
            for (int i = 0; i < num; i++) {
                ssData.ssInfo[i] = p.readInt();
                riljLog("[SS Data] SS Info " + i + " : " + ssData.ssInfo[i]);
            }
        } else {
            ssData.cfInfo = new CallForwardInfo[num];
            for (int i2 = 0; i2 < num; i2++) {
                ssData.cfInfo[i2] = new CallForwardInfo();
                ssData.cfInfo[i2].status = p.readInt();
                ssData.cfInfo[i2].reason = p.readInt();
                ssData.cfInfo[i2].serviceClass = p.readInt();
                ssData.cfInfo[i2].toa = p.readInt();
                ssData.cfInfo[i2].number = p.readString();
                ssData.cfInfo[i2].timeSeconds = p.readInt();
                riljLog("[SS Data] CF Info " + i2 + " : " + ssData.cfInfo[i2]);
            }
        }
        return ssData;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDeviceIdentity(Message response) {
        RILRequest rr = RILRequest.obtain(98, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCDMASubscription(Message response) {
        RILRequest rr = RILRequest.obtain(95, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPhoneType(int phoneType) {
        riljLog("setPhoneType=" + phoneType + " old value=" + this.mPhoneType);
        this.mPhoneType = phoneType;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCdmaRoamingPreference(Message response) {
        RILRequest rr = RILRequest.obtain(79, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        RILRequest rr = RILRequest.obtain(78, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaRoamingType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaRoamingType);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaSubscriptionSource(int cdmaSubscription, Message response) {
        RILRequest rr = RILRequest.obtain(77, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaSubscription);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaSubscription);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaSubscriptionSource(Message response) {
        RILRequest rr = RILRequest.obtain(104, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryTTYMode(Message response) {
        RILRequest rr = RILRequest.obtain(81, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setTTYMode(int ttyMode, Message response) {
        RILRequest rr = RILRequest.obtain(80, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(ttyMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + ttyMode);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        RILRequest rr = RILRequest.obtain(84, response);
        rr.mParcel.writeString(FeatureCode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + FeatureCode);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaBroadcastConfig(Message response) {
        send(RILRequest.obtain(92, response));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        RILRequest rr = RILRequest.obtain(93, response);
        ArrayList<CdmaSmsBroadcastConfigInfo> processedConfigs = new ArrayList<>();
        for (CdmaSmsBroadcastConfigInfo config : configs) {
            for (int i = config.getFromServiceCategory(); i <= config.getToServiceCategory(); i++) {
                processedConfigs.add(new CdmaSmsBroadcastConfigInfo(i, i, config.getLanguage(), config.isSelected()));
            }
        }
        CdmaSmsBroadcastConfigInfo[] rilConfigs = (CdmaSmsBroadcastConfigInfo[]) processedConfigs.toArray(configs);
        rr.mParcel.writeInt(rilConfigs.length);
        for (int i2 = 0; i2 < rilConfigs.length; i2++) {
            rr.mParcel.writeInt(rilConfigs[i2].getFromServiceCategory());
            rr.mParcel.writeInt(rilConfigs[i2].getLanguage());
            rr.mParcel.writeInt(rilConfigs[i2].isSelected() ? 1 : 0);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + rilConfigs.length + " configs : ");
        for (CdmaSmsBroadcastConfigInfo cdmaSmsBroadcastConfigInfo : rilConfigs) {
            riljLog(cdmaSmsBroadcastConfigInfo.toString());
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(94, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void exitEmergencyCallbackMode(Message response) {
        RILRequest rr = RILRequest.obtain(99, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void requestIsimAuthentication(String nonce, Message response) {
        RILRequest rr = RILRequest.obtain(105, response);
        rr.mParcel.writeString(nonce);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        RILRequest rr = RILRequest.obtain(125, response);
        rr.mParcel.writeInt(authContext);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCellInfoList(Message result) {
        RILRequest rr = RILRequest.obtain(109, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCellInfoListRate(int rateInMillis, Message response) {
        riljLog("setCellInfoListRate: " + rateInMillis);
        RILRequest rr = RILRequest.obtain(110, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(rateInMillis);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        RILRequest rr = RILRequest.obtain(111, result);
        riljLog("Set RIL_REQUEST_SET_INITIAL_ATTACH_APN");
        if (authType == -1) {
            if (TextUtils.isEmpty(username)) {
                authType = 0;
            } else {
                authType = 3;
            }
        }
        this.mSetInitialAttachApn_Apn = apn;
        this.mSetInitialAttachApn_Protocol = protocol;
        this.mSetInitialAttachApn_AuthType = authType;
        this.mSetInitialAttachApn_Username = username;
        this.mSetInitialAttachApn_Password = password;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = false;
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(protocol);
        rr.mParcel.writeInt(authType);
        rr.mParcel.writeString(username);
        rr.mParcel.writeString(password);
        String log_apn = apn;
        String log_user = username;
        String log_password = password;
        if (!"eng".equals(Build.TYPE)) {
            if (log_apn != null) {
                log_apn = "*****";
            }
            if (log_user != null) {
                log_user = "*****";
            }
            if (log_password != null) {
                log_password = "*****";
            }
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ", apn:" + log_apn + ", protocol:" + protocol + ", authType:" + authType + ", username:" + log_user + ", password:" + log_password);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setDataProfile(DataProfile[] dps, Message result) {
        riljLog("Set RIL_REQUEST_SET_DATA_PROFILE");
        RILRequest rr = RILRequest.obtain(128, null);
        DataProfile.toParcel(rr.mParcel, dps);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + dps + " Data Profiles : ");
        for (DataProfile dataProfile : dps) {
            riljLog(dataProfile.toString());
        }
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RIL: " + this);
        pw.println(" mSocket=" + this.mSocket);
        pw.println(" mSenderThread=" + this.mSenderThread);
        pw.println(" mSender=" + this.mSender);
        pw.println(" mReceiverThread=" + this.mReceiverThread);
        pw.println(" mReceiver=" + this.mReceiver);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            synchronized (this.mWakeLock) {
                pw.println(" mWakeLockCount=" + this.mWakeLockCount);
            }
            int count = this.mRequestList.size();
            pw.println(" mRequestList count=" + count);
            for (int i = 0; i < count; i++) {
                RILRequest rr = this.mRequestList.valueAt(i);
                pw.println("  [" + rr.mSerial + "] " + requestToString(rr.mRequest));
            }
        }
        pw.println(" mLastNITZTimeInfo=" + this.mLastNITZTimeInfo);
        pw.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccOpenLogicalChannel(String AID, Message response) {
        RILRequest rr = RILRequest.obtain(115, response);
        rr.mParcel.writeString(AID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccCloseLogicalChannel(int channel, Message response) {
        RILRequest rr = RILRequest.obtain(116, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(channel);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        if (channel <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        iccTransmitApduHelper(117, channel, cla, instruction, p1, p2, p3, data, response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        iccTransmitApduHelper(114, 0, cla, instruction, p1, p2, p3, data, response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getAtr(Message response) {
        RILRequest rr = RILRequest.obtain(133, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> iccGetAtr: " + requestToString(rr.mRequest) + " 0");
        send(rr);
    }

    private void iccTransmitApduHelper(int rilCommand, int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        RILRequest rr = RILRequest.obtain(rilCommand, response);
        rr.mParcel.writeInt(channel);
        rr.mParcel.writeInt(cla);
        rr.mParcel.writeInt(instruction);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvReadItem(int itemID, Message response) {
        RILRequest rr = RILRequest.obtain(118, response);
        rr.mParcel.writeInt(itemID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        RILRequest rr = RILRequest.obtain(119, response);
        rr.mParcel.writeInt(itemID);
        rr.mParcel.writeString(itemValue);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID + ": " + itemValue);
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        RILRequest rr = RILRequest.obtain(120, response);
        rr.mParcel.writeByteArray(preferredRoamingList);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " (" + preferredRoamingList.length + " bytes)");
        send(rr);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void nvResetConfig(int resetType, Message response) {
        RILRequest rr = RILRequest.obtain(121, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(resetType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + resetType);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setRatModeOptimizeSetting(boolean enable, Message response) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591827);
        reqBuffer.putInt(4);
        reqBuffer.putInt(enable ? 1 : 0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] setRatModeOptimizeSetting: " + enable);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getPreferredNetworkTypeWithOptimizeSetting(Message response) {
        byte[] request = new byte[this.mHeaderSize + 0];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591826);
        reqBuffer.putInt(0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] getPreferredNetworkTypeWithOptimizeSetting is called.");
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setBandPref(long lteBand, int wcdmaBand, Message response) {
        byte[] request = new byte[this.mHeaderSize + 12];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591830);
        reqBuffer.putInt(12);
        reqBuffer.putLong(lteBand);
        reqBuffer.putInt(wcdmaBand);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] setBandPref lteBand: " + lteBand + " wcdmaBand: " + wcdmaBand);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getBandPref(Message response) {
        byte[] request = new byte[this.mHeaderSize + 0];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591831);
        reqBuffer.putInt(0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] getBandPref is called.");
    }

    private void setLimitedService(Message response) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591829);
        reqBuffer.putInt(4);
        reqBuffer.putInt(this.mLimitedService ? 1 : 0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] setLimitedService: " + this.mLimitedService);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setLimitationByChameleon(boolean isLimitation, Message response) {
        this.mSetLimitationByChameleon = true;
        this.mLimitationByChameleon = isLimitation;
        this.mLimitedService = this.mLimitationByChameleon;
        setLimitedService(response);
        riljLog("[EXTDBG] setLimitationByChameleon: " + this.mLimitationByChameleon);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void updateOemDataSettings(boolean mobileData, boolean dataRoaming, boolean epcCapability, Message response) {
        riljLog("[EXTDBG] RIL_REQUEST_OEM_HOOK_RAW_UPDATE_LTE_APN > " + mobileData + " " + dataRoaming);
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = false;
        this.mUpdateOemDataSettings_MobileData = mobileData;
        this.mUpdateOemDataSettings_DataRoaming = dataRoaming;
        byte[] request = new byte[this.mHeaderSize + 3];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591828);
        reqBuffer.putInt(3);
        if (mobileData) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        if (dataRoaming) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        if (epcCapability) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] updateOemDataSettings is called.");
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccExchangeAPDU(int cla, int command, int channel, int p1, int p2, int p3, String data, Message result) {
        RILRequest rr;
        if (channel < 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        if (channel == 0) {
            rr = RILRequest.obtain(134, result);
        } else {
            rr = RILRequest.obtain(137, result);
        }
        rr.mParcel.writeInt(channel);
        rr.mParcel.writeInt(cla);
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccOpenChannel(String AID, Message result) {
        RILRequest rr = RILRequest.obtain(135, result);
        rr.mParcel.writeString(AID);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccCloseChannel(int channel, Message result) {
        RILRequest rr = RILRequest.obtain(136, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(channel);
        send(rr);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public boolean isRunning() {
        boolean transitionedSender = false;
        boolean transitionedReceiver = false;
        if (this.mSenderThread != null && this.mSenderThread.isAlive()) {
            transitionedSender = true;
        }
        if (this.mReceiverThread != null && this.mReceiverThread.isAlive()) {
            transitionedReceiver = true;
        }
        return transitionedSender && transitionedReceiver;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void upDomesticInService(Context context, Intent intent) {
        riljLog("upDomesticInService start ");
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());
        if (ss != null) {
            riljLog("upDomesticInService gsm.domesticinservice before: " + String.valueOf(isDomesticInService()));
            int state = ss.getState();
            riljLog("upDomesticInService state : " + Integer.toString(state));
            if (state == 0) {
                switch (this.mPhoneType) {
                    case 1:
                        riljLog("upDomesticInService PhoneType : GSM_PHONE");
                        SystemProperties.set("gsm.domesticinservice", String.valueOf(new TelephonyManager(context).getNetworkCountryIso().equals("jp")));
                        break;
                    case 2:
                        riljLog("upDomesticInService PhoneType : CDMA_PHONE");
                        SystemProperties.set("gsm.domesticinservice", String.valueOf(!new TelephonyManager(context).isNetworkRoaming()));
                        break;
                    default:
                        riljLog("upDomesticInService PhoneType : Outside the scope PhoneType: " + Integer.toString(this.mPhoneType));
                        break;
                }
                riljLog("upDomesticInService gsm.domesticinservice after: " + String.valueOf(isDomesticInService()));
            }
        }
        riljLog("upDomesticInService end ");
    }

    public static boolean isDomesticInService() {
        return SystemProperties.getBoolean("gsm.domesticinservice", false);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void reportDecryptStatus(boolean decrypt, Message result) {
        if (TelBrand.IS_DCM) {
            RILRequest rr = RILRequest.obtain(102, result);
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(decrypt ? 3 : 2);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + decrypt);
            send(rr);
        }
    }

    protected void notifyLteBandInfo(byte[] data) {
        if (this.mLteBandInfoRegistrant != null) {
            ByteBuffer lteBandInfo = ByteBuffer.wrap(data);
            lteBandInfo.order(ByteOrder.nativeOrder());
            this.mLteBandInfoRegistrant.notifyRegistrant(new AsyncResult((Object) null, new Integer(lteBandInfo.getInt()), (Throwable) null));
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class RILRadioLogWriter implements Runnable {
        public static final int LOGBUF_MAIN = 0;
        public static final int LOGBUF_RADIO = 1;
        public static final int LOGBUF_RIL_CMD_INFO = 2;
        private static final int RADIO_LOG_BUFFERSIZE = 262144;
        private static final int RADIO_LOG_REC_SIZE_TYPE1 = 8192;
        private static final int RADIO_LOG_REC_SIZE_TYPE2 = 204800;
        public static final int RECTYPE_NETWORK_SELECTION = 2;
        public static final int RECTYPE_SETNETWORK = 0;
        public static final int RECTYPE_SHUTDOWN = 1;
        private int mBufType;
        private int mRecType;
        private int mWriteSize;
        private String mStackTrace = null;
        private String mAppendString = null;

        public RILRadioLogWriter(int rectype, int buftype) {
            if (TelBrand.IS_DCM) {
                if (rectype == 0) {
                    this.mRecType = 0;
                    this.mWriteSize = 8192;
                } else if (rectype == 2) {
                    this.mRecType = 2;
                    this.mWriteSize = 8192;
                } else {
                    this.mRecType = 1;
                    this.mWriteSize = RADIO_LOG_REC_SIZE_TYPE2;
                }
                if (buftype == 0) {
                    this.mBufType = 0;
                } else if (buftype == 2) {
                    this.mBufType = 2;
                } else {
                    this.mBufType = 1;
                }
            }
        }

        public void setStackTrace() {
            if (TelBrand.IS_DCM) {
                this.mStackTrace = Log.getStackTraceString(new Throwable());
            }
        }

        public void setLog(String log) {
            if (TelBrand.IS_DCM) {
                this.mAppendString = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()) + " : " + log + "\n";
            }
        }

        /* JADX WARN: Removed duplicated region for block: B:178:0x06c9  */
        /* JADX WARN: Removed duplicated region for block: B:213:0x00ed A[EXC_TOP_SPLITTER, SYNTHETIC] */
        /* JADX WARN: Removed duplicated region for block: B:219:0x04d7 A[EXC_TOP_SPLITTER, SYNTHETIC] */
        /* JADX WARN: Removed duplicated region for block: B:231:0x00d3 A[EXC_TOP_SPLITTER, SYNTHETIC] */
        /* JADX WARN: Removed duplicated region for block: B:233:0x049d A[EXC_TOP_SPLITTER, SYNTHETIC] */
        @Override // java.lang.Runnable
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public void run() {
            /*
                Method dump skipped, instructions count: 1745
                To view this dump change 'Code comments level' option to 'DEBUG'
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.RIL.RILRadioLogWriter.run():void");
        }

        private String getPrefix(int rectype, int buftype) {
            String ret;
            if (!TelBrand.IS_DCM) {
                return "";
            }
            if (rectype == 0) {
                ret = "N";
            } else if (rectype == 2) {
                ret = "NS";
            } else {
                ret = "S";
            }
            if (buftype == 0) {
                return ret + "M";
            }
            if (buftype == 2) {
                return ret + "RCI";
            }
            return ret + "R";
        }

        private synchronized int getRadioLogIndex(String path, int rectype, int buftype) {
            byte[] bytes;
            int idx;
            Throwable th;
            IOException e;
            byte[] bytes2;
            if (!TelBrand.IS_DCM) {
                idx = -1;
            } else {
                InputStream in = null;
                int RADIO_LOG_RINGSIZE = 10;
                int idx2 = 10 - 1;
                String RADIO_LOG_FILENAME_INDEX = path + getPrefix(rectype, buftype) + "1000";
                if (rectype == 0) {
                    RADIO_LOG_RINGSIZE = 30;
                    idx2 = 30 - 1;
                } else if (rectype == 2) {
                    RADIO_LOG_RINGSIZE = 30;
                    idx2 = 30 - 1;
                }
                try {
                    in = new FileInputStream(RADIO_LOG_FILENAME_INDEX);
                } catch (FileNotFoundException e2) {
                    Rlog.w(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " open err:" + e2);
                }
                if (in != null) {
                    try {
                        try {
                            if (in.available() > 0 && (bytes = new byte[in.available()]) != null) {
                                in.read(bytes);
                                String str = new String(bytes, "US-ASCII");
                                if (str != null) {
                                    idx2 = Integer.valueOf(str).intValue();
                                    if (idx2 < 0 || idx2 >= RADIO_LOG_RINGSIZE) {
                                        idx2 = RADIO_LOG_RINGSIZE - 1;
                                    }
                                }
                            }
                            try {
                                in.close();
                            } catch (IOException e3) {
                            }
                        } catch (NumberFormatException nfe) {
                            Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " read err:" + nfe);
                            try {
                                in.close();
                            } catch (IOException e4) {
                            }
                        }
                    } catch (IOException e5) {
                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " read err:" + e5);
                        try {
                            in.close();
                        } catch (IOException e6) {
                        }
                    }
                }
                idx = (idx2 + 1) % RADIO_LOG_RINGSIZE;
                try {
                    OutputStream out = null;
                    try {
                        OutputStream out2 = new FileOutputStream(RADIO_LOG_FILENAME_INDEX);
                        if (out2 != null) {
                            try {
                                String str2 = String.valueOf(idx);
                                if (!(str2 == null || (bytes2 = str2.getBytes("US-ASCII")) == null)) {
                                    out2.write(bytes2);
                                    out2.flush();
                                }
                            } catch (IOException e7) {
                                e = e7;
                                out = out2;
                                Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " write err:" + e);
                                if (out != null) {
                                    try {
                                        out.close();
                                    } catch (IOException e8) {
                                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e8);
                                    }
                                }
                                return idx;
                            } catch (Throwable th2) {
                                th = th2;
                                out = out2;
                                if (out != null) {
                                    try {
                                        out.close();
                                    } catch (IOException e9) {
                                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e9);
                                    }
                                }
                                throw th;
                            }
                        }
                        if (out2 != null) {
                            try {
                                out2.close();
                                out = out2;
                            } catch (IOException e10) {
                                Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e10);
                                out = out2;
                            }
                        } else {
                            out = out2;
                        }
                    } catch (IOException e11) {
                        e = e11;
                    }
                } catch (Throwable th3) {
                    th = th3;
                }
            }
            return idx;
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void getSimLock(Message response) {
        byte[] request = new byte[this.mHeaderSize + 0];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(592030);
        reqBuffer.putInt(0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] getSimLock");
    }

    public boolean replaceInvokeOemRilRequestRaw(byte[] data, Message response) {
        ByteBuffer readBuffer = ByteBuffer.wrap(data);
        readBuffer.order(ByteOrder.nativeOrder());
        byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
        readBuffer.get(oemIdBytes);
        if (new String(oemIdBytes).equals(OEM_IDENTIFIER)) {
            return false;
        }
        switch (readBuffer.getInt(0)) {
            case OemCdmaTelephonyManager.OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD /* 33554442 */:
                validateMsl(Arrays.copyOfRange(data, 18, 24), response);
                return true;
            default:
                return false;
        }
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void setModemSettingsByChameleon(int pattern, Message response) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591838);
        reqBuffer.putInt(4);
        reqBuffer.putInt(pattern);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] pattern: " + pattern);
    }

    private void validateMsl(byte[] msl, Message response) {
        byte[] request = new byte[this.mHeaderSize + 6];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591837);
        reqBuffer.putInt(6);
        for (int i = 0; i < 6; i++) {
            reqBuffer.put(msl[i]);
        }
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] validateMsl: " + IccUtils.bytesToHexString(msl));
    }
}
