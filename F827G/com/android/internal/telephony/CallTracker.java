package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.PhoneConstants.State;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class CallTracker extends Handler {
    private static final boolean DBG_POLL = false;
    protected static final int EVENT_CALL_STATE_CHANGE = 2;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA = 15;
    protected static final int EVENT_CONFERENCE_RESULT = 11;
    protected static final int EVENT_ECT_RESULT = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA = 14;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE = 5;
    protected static final int EVENT_HANGUP_RESULT = 18;
    protected static final int EVENT_OPERATION_COMPLETE = 4;
    protected static final int EVENT_POLL_CALLS_RESULT = 1;
    protected static final int EVENT_RADIO_AVAILABLE = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE = 10;
    protected static final int EVENT_REPOLL_AFTER_DELAY = 3;
    protected static final int EVENT_SEPARATE_RESULT = 12;
    protected static final int EVENT_SWITCH_RESULT = 8;
    protected static final int EVENT_THREE_WAY_DIAL_BLANK_FLASH = 20;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;
    static final int POLL_DELAY_MSEC = 250;
    private final int VALID_COMPARE_LENGTH = 3;
    public CommandsInterface mCi;
    protected ArrayList<Connection> mHandoverConnections = new ArrayList();
    protected Message mLastRelevantPoll;
    protected boolean mNeedsPoll;
    protected boolean mNumberConverted = false;
    protected int mPendingOperations;

    private boolean checkNoOperationsPending() {
        return this.mPendingOperations == 0;
    }

    private boolean compareGid1(PhoneBase phoneBase, String str) {
        boolean z = true;
        String groupIdLevel1 = phoneBase.getGroupIdLevel1();
        int length = str.length();
        if (str == null || str.equals("")) {
            log("compareGid1 serviceGid is empty, return " + true);
        } else {
            if (groupIdLevel1 == null || groupIdLevel1.length() < length || !groupIdLevel1.substring(0, length).equalsIgnoreCase(str)) {
                log(" gid1 " + groupIdLevel1 + " serviceGid1 " + str);
                z = false;
            }
            log("compareGid1 is " + (z ? "Same" : "Different"));
        }
        return z;
    }

    /* Access modifiers changed, original: protected */
    public String checkForTestEmergencyNumber(String str) {
        String str2 = SystemProperties.get("ril.test.emergencynumber");
        if (TextUtils.isEmpty(str2)) {
            return str;
        }
        String[] split = str2.split(":");
        log("checkForTestEmergencyNumber: values.length=" + split.length);
        if (split.length != 2 || !split[0].equals(PhoneNumberUtils.stripSeparators(str))) {
            return str;
        }
        this.mCi.testingEmergencyCall();
        log("checkForTestEmergencyNumber: remap " + str + " to " + split[1]);
        return split[1];
    }

    /* Access modifiers changed, original: protected */
    public String convertNumberIfNecessary(PhoneBase phoneBase, String str) {
        if (str == null) {
            return str;
        }
        String[] stringArray = phoneBase.getContext().getResources().getStringArray(17236029);
        log("convertNumberIfNecessary Roaming convertMaps.length " + stringArray.length + " dialNumber.length() " + str.length());
        if (stringArray.length < 1 || str.length() < 3) {
            return str;
        }
        String str2 = "";
        int length = stringArray.length;
        int i = 0;
        int i2 = 0;
        while (i < length) {
            int i3;
            String str3 = stringArray[i];
            log("convertNumberIfNecessary: " + str3);
            String[] split = str3.split(":");
            if (split.length > 1) {
                String[] split2 = split[1].split(",");
                if (!TextUtils.isEmpty(split[0]) && str.equals(split[0])) {
                    if (split2.length < 2 || TextUtils.isEmpty(split2[1])) {
                        if (str2.isEmpty()) {
                            i2 = 1;
                        }
                    } else if (compareGid1(phoneBase, split2[1])) {
                        i2 = 1;
                    }
                    if (i2 != 0) {
                        String str4;
                        if (TextUtils.isEmpty(split2[0]) || !split2[0].endsWith("MDN")) {
                            str4 = split2[0];
                        } else {
                            str4 = phoneBase.getLine1Number();
                            if (TextUtils.isEmpty(str4)) {
                                str4 = str2;
                            } else if (!str4.startsWith("+")) {
                                str4 = split2[0].substring(0, split2[0].length() - 3) + str4;
                            }
                        }
                        i3 = 0;
                        str2 = str4;
                        i++;
                        i2 = i3;
                    }
                }
            }
            i3 = i2;
            i++;
            i2 = i3;
        }
        if (TextUtils.isEmpty(str2)) {
            return str;
        }
        log("convertNumberIfNecessary: convert service number");
        this.mNumberConverted = true;
        return str2;
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("CallTracker:");
        printWriter.println(" mPendingOperations=" + this.mPendingOperations);
        printWriter.println(" mNeedsPoll=" + this.mNeedsPoll);
        printWriter.println(" mLastRelevantPoll=" + this.mLastRelevantPoll);
    }

    /* Access modifiers changed, original: protected */
    public Connection getHoConnection(DriverCall driverCall) {
        Connection connection;
        Iterator it = this.mHandoverConnections.iterator();
        while (it.hasNext()) {
            connection = (Connection) it.next();
            log("getHoConnection - compare number: hoConn= " + connection.toString());
            if (connection.getAddress() != null && connection.getAddress().contains(driverCall.number)) {
                log("getHoConnection: Handover connection match found = " + connection.toString());
                return connection;
            }
        }
        it = this.mHandoverConnections.iterator();
        while (it.hasNext()) {
            connection = (Connection) it.next();
            log("getHoConnection: compare state hoConn= " + connection.toString());
            if (connection.getStateBeforeHandover() == Call.stateFromDCState(driverCall.state)) {
                log("getHoConnection: Handover connection match found = " + connection.toString());
                return connection;
            }
        }
        return null;
    }

    public abstract State getState();

    public abstract void handleMessage(Message message);

    public abstract void handlePollCalls(AsyncResult asyncResult);

    /* Access modifiers changed, original: protected */
    public void handleRadioAvailable() {
        pollCallsWhenSafe();
    }

    /* Access modifiers changed, original: protected */
    public boolean isCommandExceptionRadioNotAvailable(Throwable th) {
        return th != null && (th instanceof CommandException) && ((CommandException) th).getCommandError() == Error.RADIO_NOT_AVAILABLE;
    }

    public abstract void log(String str);

    /* Access modifiers changed, original: protected */
    public void notifySrvccState(SrvccState srvccState, ArrayList<Connection> arrayList) {
        if (srvccState == SrvccState.STARTED && arrayList != null) {
            this.mHandoverConnections.addAll(arrayList);
        } else if (srvccState != SrvccState.COMPLETED) {
            this.mHandoverConnections.clear();
        }
        log("notifySrvccState: mHandoverConnections= " + this.mHandoverConnections.toString());
    }

    /* Access modifiers changed, original: protected */
    public Message obtainNoPollCompleteMessage(int i) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        return obtainMessage(i);
    }

    /* Access modifiers changed, original: protected */
    public void pollCallsAfterDelay() {
        Message obtainMessage = obtainMessage();
        obtainMessage.what = 3;
        sendMessageDelayed(obtainMessage, 250);
    }

    /* Access modifiers changed, original: protected */
    public void pollCallsWhenSafe() {
        this.mNeedsPoll = true;
        if (checkNoOperationsPending()) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        }
    }

    public abstract void registerForVoiceCallEnded(Handler handler, int i, Object obj);

    public abstract void registerForVoiceCallStarted(Handler handler, int i, Object obj);

    public abstract void unregisterForVoiceCallEnded(Handler handler);

    public abstract void unregisterForVoiceCallStarted(Handler handler);
}
