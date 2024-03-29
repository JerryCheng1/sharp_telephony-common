package com.android.internal.telephony;

import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum State {
        ACTIVE,
        HOLDING,
        DIALING,
        ALERTING,
        INCOMING,
        WAITING
    }

    static DriverCall fromCLCCLine(String line) {
        boolean z = true;
        DriverCall ret = new DriverCall();
        ATResponseParser p = new ATResponseParser(line);
        try {
            ret.index = p.nextInt();
            ret.isMT = p.nextBoolean();
            ret.state = stateFromCLCC(p.nextInt());
            if (p.nextInt() != 0) {
                z = false;
            }
            ret.isVoice = z;
            ret.isMpty = p.nextBoolean();
            ret.numberPresentation = 1;
            if (!p.hasMore()) {
                return ret;
            }
            ret.number = PhoneNumberUtils.extractNetworkPortionAlt(p.nextString());
            if (ret.number.length() == 0) {
                ret.number = null;
            }
            ret.TOA = p.nextInt();
            ret.number = PhoneNumberUtils.stringFromStringAndTOA(ret.number, ret.TOA);
            return ret;
        } catch (ATParseEx e) {
            Rlog.e(LOG_TAG, "Invalid CLCC line: '" + line + "'");
            return null;
        }
    }

    public String toString() {
        if ("eng".equals(Build.TYPE)) {
            return "id=" + this.index + "," + this.state + ",toa=" + this.TOA + "," + (this.isMpty ? "conf" : "norm") + "," + (this.isMT ? "mt" : "mo") + "," + this.als + "," + (this.isVoice ? "voc" : "nonvoc") + "," + (this.isVoicePrivacy ? "evp" : "noevp") + ",number=" + this.number + ",cli=" + this.numberPresentation + ",name=" + this.name + "," + this.namePresentation;
        }
        return "id=" + this.index + "," + this.state + ",toa=" + this.TOA + "," + (this.isMpty ? "conf" : "norm") + "," + (this.isMT ? "mt" : "mo") + "," + this.als + "," + (this.isVoice ? "voc" : "nonvoc") + "," + (this.isVoicePrivacy ? "evp" : "noevp") + ",,cli=" + this.numberPresentation + ",," + this.namePresentation;
    }

    public static State stateFromCLCC(int state) throws ATParseEx {
        switch (state) {
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
                throw new ATParseEx("illegal call state " + state);
        }
    }

    public static int presentationFromCLIP(int cli) throws ATParseEx {
        switch (cli) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            default:
                throw new ATParseEx("illegal presentation " + cli);
        }
    }

    public int compareTo(DriverCall dc) {
        if (this.index < dc.index) {
            return -1;
        }
        if (this.index == dc.index) {
            return 0;
        }
        return 1;
    }
}
