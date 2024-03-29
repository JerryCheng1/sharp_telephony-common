package com.android.internal.telephony;

import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.PhoneConstants;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public interface PhoneNotifier {
    void notifyCallForwardingChanged(Phone phone);

    void notifyCellInfo(Phone phone, List<CellInfo> list);

    void notifyCellLocation(Phone phone);

    void notifyDataActivity(Phone phone);

    void notifyDataConnection(Phone phone, String str, String str2, PhoneConstants.DataState dataState);

    void notifyDataConnectionFailed(Phone phone, String str, String str2);

    void notifyDataConnectionRealTimeInfo(Phone phone, DataConnectionRealTimeInfo dataConnectionRealTimeInfo);

    void notifyDisconnectCause(int i, int i2);

    void notifyMessageWaitingChanged(Phone phone);

    void notifyOemHookRawEventForSubscriber(int i, byte[] bArr);

    void notifyOtaspChanged(Phone phone, int i);

    void notifyPhoneState(Phone phone);

    void notifyPreciseCallState(Phone phone);

    void notifyPreciseDataConnectionFailed(Phone phone, String str, String str2, String str3, String str4);

    void notifyServiceState(Phone phone);

    void notifySignalStrength(Phone phone);

    void notifyVoLteServiceStateChanged(Phone phone, VoLteServiceState voLteServiceState);
}
