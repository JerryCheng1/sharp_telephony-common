package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;

public interface IccCard {
    void changeIccFdnPassword(String str, String str2, Message message);

    void changeIccLockPassword(String str, String str2, Message message);

    void closeLogicalChannel(int i, Message message);

    void exchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message);

    void exchangeSimIO(int i, int i2, int i3, int i4, int i5, String str, Message message);

    void getEfLock(Message message);

    boolean getIccFdnAvailable();

    boolean getIccFdnEnabled();

    IccFileHandler getIccFileHandler();

    boolean getIccLockEnabled();

    boolean getIccPin2Blocked();

    int getIccPinPukRetryCountSc(int i);

    boolean getIccPuk2Blocked();

    IccRecords getIccRecords();

    String getServiceProviderName();

    int getSimLock();

    State getState();

    boolean hasIccCard();

    boolean isApplicationOnIcc(AppType appType);

    void openLogicalChannel(String str, Message message);

    void registerForAbsent(Handler handler, int i, Object obj);

    void registerForLocked(Handler handler, int i, Object obj);

    void registerForNetworkLocked(Handler handler, int i, Object obj);

    void setIccFdnEnabled(boolean z, String str, Message message);

    void setIccLockEnabled(boolean z, String str, Message message);

    void setPinPukRetryCount(int i, int i2);

    void supplyNetworkDepersonalization(String str, Message message);

    void supplyPin(String str, Message message);

    void supplyPin2(String str, Message message);

    void supplyPuk(String str, String str2, Message message);

    void supplyPuk2(String str, String str2, Message message);

    void unregisterForAbsent(Handler handler);

    void unregisterForLocked(Handler handler);

    void unregisterForNetworkLocked(Handler handler);
}
