package com.android.internal.telephony;

import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

public class DriverCall implements Comparable<DriverCall> {
    static final String LOG_TAG = "DriverCall";
    public int TOA;
    public int als;
    public int index;
    public boolean isMT;
    public boolean isMpty;
    public boolean isVoice;
    public boolean isVoicePrivacy;
    public String name;
    public int namePresentation;
    public String number;
    public int numberPresentation;
    public State state;
    public UUSInfo uusInfo;

    public enum State {
        ACTIVE,
        HOLDING,
        DIALING,
        ALERTING,
        INCOMING,
        WAITING
    }

    static DriverCall fromCLCCLine(String str) {
        boolean z = true;
        DriverCall driverCall = new DriverCall();
        ATResponseParser aTResponseParser = new ATResponseParser(str);
        try {
            driverCall.index = aTResponseParser.nextInt();
            driverCall.isMT = aTResponseParser.nextBoolean();
            driverCall.state = stateFromCLCC(aTResponseParser.nextInt());
            if (aTResponseParser.nextInt() != 0) {
                z = false;
            }
            driverCall.isVoice = z;
            driverCall.isMpty = aTResponseParser.nextBoolean();
            driverCall.numberPresentation = 1;
            if (!aTResponseParser.hasMore()) {
                return driverCall;
            }
            driverCall.number = PhoneNumberUtils.extractNetworkPortionAlt(aTResponseParser.nextString());
            if (driverCall.number.length() == 0) {
                driverCall.number = null;
            }
            driverCall.TOA = aTResponseParser.nextInt();
            driverCall.number = PhoneNumberUtils.stringFromStringAndTOA(driverCall.number, driverCall.TOA);
            return driverCall;
        } catch (ATParseEx e) {
            Rlog.e(LOG_TAG, "Invalid CLCC line: '" + str + "'");
            return null;
        }
    }

    public static int presentationFromCLIP(int i) throws ATParseEx {
        switch (i) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            default:
                throw new ATParseEx("illegal presentation " + i);
        }
    }

    public static State stateFromCLCC(int i) throws ATParseEx {
        switch (i) {
            case 0:
                return State.ACTIVE;
            case 1:
                return State.HOLDING;
            case 2:
                return State.DIALING;
            case 3:
                return State.ALERTING;
            case 4:
                return State.INCOMING;
            case 5:
                return State.WAITING;
            default:
                throw new ATParseEx("illegal call state " + i);
        }
    }

    public int compareTo(DriverCall driverCall) {
        return this.index < driverCall.index ? -1 : this.index == driverCall.index ? 0 : 1;
    }

    public String toString() {
        if ("eng".equals(Build.TYPE)) {
            return "id=" + this.index + "," + this.state + "," + "toa=" + this.TOA + "," + (this.isMpty ? "conf" : "norm") + "," + (this.isMT ? "mt" : "mo") + "," + this.als + "," + (this.isVoice ? "voc" : "nonvoc") + "," + (this.isVoicePrivacy ? "evp" : "noevp") + "," + "number=" + this.number + ",cli=" + this.numberPresentation + "," + "name=" + this.name + "," + this.namePresentation;
        }
        return "id=" + this.index + "," + this.state + "," + "toa=" + this.TOA + "," + (this.isMpty ? "conf" : "norm") + "," + (this.isMT ? "mt" : "mo") + "," + this.als + "," + (this.isVoice ? "voc" : "nonvoc") + "," + (this.isVoicePrivacy ? "evp" : "noevp") + "," + ",cli=" + this.numberPresentation + "," + "," + this.namePresentation;
    }
}
