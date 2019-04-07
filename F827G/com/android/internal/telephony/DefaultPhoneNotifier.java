package com.android.internal.telephony;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.ITelephonyRegistry.Stub;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import java.util.List;

public class DefaultPhoneNotifier implements PhoneNotifier {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "DefaultPhoneNotifier";
    protected ITelephonyRegistry mRegistry = Stub.asInterface(ServiceManager.getService("telephony.registry"));

    /* renamed from: com.android.internal.telephony.DefaultPhoneNotifier$1 */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState = new int[DataState.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$State = new int[State.values().length];

        static {
            $SwitchMap$com$android$internal$telephony$Call$State = new int[Call.State.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.HOLDING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.ALERTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.INCOMING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.WAITING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DISCONNECTED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DISCONNECTING.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            $SwitchMap$com$android$internal$telephony$Phone$DataActivityState = new int[DataActivityState.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$Phone$DataActivityState[DataActivityState.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Phone$DataActivityState[DataActivityState.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Phone$DataActivityState[DataActivityState.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Phone$DataActivityState[DataActivityState.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[DataState.CONNECTING.ordinal()] = 1;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[DataState.CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[DataState.SUSPENDED.ordinal()] = 3;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[State.RINGING.ordinal()] = 1;
            } catch (NoSuchFieldError e16) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[State.OFFHOOK.ordinal()] = 2;
            } catch (NoSuchFieldError e17) {
            }
        }
    }

    public interface IDataStateChangedCallback {
        void onDataStateChanged(int i, String str, String str2, String str3, String str4, boolean z);
    }

    protected DefaultPhoneNotifier() {
    }

    public static int convertCallState(State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$PhoneConstants$State[state.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            default:
                return 0;
        }
    }

    public static State convertCallState(int i) {
        switch (i) {
            case 1:
                return State.RINGING;
            case 2:
                return State.OFFHOOK;
            default:
                return State.IDLE;
        }
    }

    public static int convertDataActivityState(DataActivityState dataActivityState) {
        switch (dataActivityState) {
            case DATAIN:
                return 1;
            case DATAOUT:
                return 2;
            case DATAINANDOUT:
                return 3;
            case DORMANT:
                return 4;
            default:
                return 0;
        }
    }

    public static DataActivityState convertDataActivityState(int i) {
        switch (i) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAOUT;
            case 3:
                return DataActivityState.DATAINANDOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    public static int convertDataState(DataState dataState) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[dataState.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                return 0;
        }
    }

    public static DataState convertDataState(int i) {
        switch (i) {
            case 1:
                return DataState.CONNECTING;
            case 2:
                return DataState.CONNECTED;
            case 3:
                return DataState.SUSPENDED;
            default:
                return DataState.DISCONNECTED;
        }
    }

    public static int convertPreciseCallState(Call.State state) {
        switch (state) {
            case ACTIVE:
                return 1;
            case HOLDING:
                return 2;
            case DIALING:
                return 3;
            case ALERTING:
                return 4;
            case INCOMING:
                return 5;
            case WAITING:
                return 6;
            case DISCONNECTED:
                return 7;
            case DISCONNECTING:
                return 8;
            default:
                return 0;
        }
    }

    public static Call.State convertPreciseCallState(int i) {
        switch (i) {
            case 1:
                return Call.State.ACTIVE;
            case 2:
                return Call.State.HOLDING;
            case 3:
                return Call.State.DIALING;
            case 4:
                return Call.State.ALERTING;
            case 5:
                return Call.State.INCOMING;
            case 6:
                return Call.State.WAITING;
            case 7:
                return Call.State.DISCONNECTED;
            case 8:
                return Call.State.DISCONNECTING;
            default:
                return Call.State.IDLE;
        }
    }

    private void doNotifyDataConnection(Phone phone, String str, String str2, DataState dataState) {
        LinkProperties linkProperties;
        NetworkCapabilities networkCapabilities;
        int i = 0;
        int subId = phone.getSubId();
        SubscriptionManager.getDefaultDataSubId();
        TelephonyManager telephonyManager = TelephonyManager.getDefault();
        if (dataState == DataState.CONNECTED) {
            linkProperties = phone.getLinkProperties(str2);
            networkCapabilities = phone.getNetworkCapabilities(str2);
        } else {
            networkCapabilities = null;
            linkProperties = null;
        }
        ServiceState serviceState = phone.getServiceState();
        boolean dataRoaming = serviceState != null ? serviceState.getDataRoaming() : false;
        try {
            if (this.mRegistry != null) {
                ITelephonyRegistry iTelephonyRegistry = this.mRegistry;
                int convertDataState = convertDataState(dataState);
                boolean isDataConnectivityPossible = phone.isDataConnectivityPossible(str2);
                String activeApnHost = phone.getActiveApnHost(str2);
                if (telephonyManager != null) {
                    i = telephonyManager.getDataNetworkType(subId);
                }
                iTelephonyRegistry.notifyDataConnectionForSubscriber(subId, convertDataState, isDataConnectivityPossible, str, activeApnHost, str2, linkProperties, networkCapabilities, i, dataRoaming);
            }
        } catch (RemoteException e) {
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    public void notifyCallForwardingChanged(Phone phone) {
        int subId = phone.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCallForwardingChangedForSubscriber(subId, phone.getCallForwardingIndicator());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyCellInfo(Phone phone, List<CellInfo> list) {
        int subId = phone.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCellInfoForSubscriber(subId, list);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyCellLocation(Phone phone) {
        int subId = phone.getSubId();
        Bundle bundle = new Bundle();
        phone.getCellLocation().fillInNotifierBundle(bundle);
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCellLocationForSubscriber(subId, bundle);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataActivity(Phone phone) {
        int subId = phone.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyDataActivityForSubscriber(subId, convertDataActivityState(phone.getDataActivityState()));
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataConnection(Phone phone, String str, String str2, DataState dataState) {
        doNotifyDataConnection(phone, str, str2, dataState);
    }

    public void notifyDataConnectionFailed(Phone phone, String str, String str2) {
        int subId = phone.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyDataConnectionFailedForSubscriber(subId, str, str2);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataConnectionRealTimeInfo(Phone phone, DataConnectionRealTimeInfo dataConnectionRealTimeInfo) {
        try {
            this.mRegistry.notifyDataConnectionRealTimeInfo(dataConnectionRealTimeInfo);
        } catch (RemoteException e) {
        }
    }

    public void notifyDisconnectCause(int i, int i2) {
        try {
            this.mRegistry.notifyDisconnectCause(i, i2);
        } catch (RemoteException e) {
        }
    }

    public void notifyMessageWaitingChanged(Phone phone) {
        int phoneId = phone.getPhoneId();
        int subId = phone.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyMessageWaitingChangedForPhoneId(phoneId, subId, phone.getMessageWaitingIndicator());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyOemHookRawEventForSubscriber(int i, byte[] bArr) {
        try {
            this.mRegistry.notifyOemHookRawEventForSubscriber(i, bArr);
        } catch (RemoteException e) {
        }
    }

    public void notifyOtaspChanged(Phone phone, int i) {
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyOtaspChanged(i);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyPhoneState(Phone phone) {
        Call ringingCall = phone.getRingingCall();
        int subId = phone.getSubId();
        String str = "";
        if (!(ringingCall == null || ringingCall.getEarliestConnection() == null)) {
            str = ringingCall.getEarliestConnection().getAddress();
        }
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCallStateForSubscriber(subId, convertCallState(phone.getState()), str);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyPreciseCallState(Phone phone) {
        Call ringingCall = phone.getRingingCall();
        Call foregroundCall = phone.getForegroundCall();
        Call backgroundCall = phone.getBackgroundCall();
        if (ringingCall != null && foregroundCall != null && backgroundCall != null) {
            try {
                this.mRegistry.notifyPreciseCallState(convertPreciseCallState(ringingCall.getState()), convertPreciseCallState(foregroundCall.getState()), convertPreciseCallState(backgroundCall.getState()));
            } catch (RemoteException e) {
            }
        }
    }

    public void notifyPreciseDataConnectionFailed(Phone phone, String str, String str2, String str3, String str4) {
        try {
            this.mRegistry.notifyPreciseDataConnectionFailed(str, str2, str3, str4);
        } catch (RemoteException e) {
        }
    }

    public void notifyServiceState(Phone phone) {
        ServiceState serviceState = phone.getServiceState();
        int phoneId = phone.getPhoneId();
        int subId = phone.getSubId();
        Rlog.d(LOG_TAG, "nofityServiceState: mRegistry=" + this.mRegistry + " ss=" + serviceState + " sender=" + phone + " phondId=" + phoneId + " subId=" + subId);
        if (serviceState == null) {
            serviceState = new ServiceState();
            serviceState.setStateOutOfService();
        }
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyServiceStateForPhoneId(phoneId, subId, serviceState);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifySignalStrength(Phone phone) {
        int subId = phone.getSubId();
        Rlog.d(LOG_TAG, "notifySignalStrength: mRegistry=" + this.mRegistry + " ss=" + phone.getSignalStrength() + " sender=" + phone);
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifySignalStrengthForSubscriber(subId, phone.getSignalStrength());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyVoLteServiceStateChanged(Phone phone, VoLteServiceState voLteServiceState) {
        try {
            this.mRegistry.notifyVoLteServiceStateChanged(voLteServiceState);
        } catch (RemoteException e) {
        }
    }
}
