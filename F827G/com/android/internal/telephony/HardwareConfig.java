package com.android.internal.telephony;

import java.util.BitSet;

public class HardwareConfig {
    public static final int DEV_HARDWARE_STATE_DISABLED = 2;
    public static final int DEV_HARDWARE_STATE_ENABLED = 0;
    public static final int DEV_HARDWARE_STATE_STANDBY = 1;
    public static final int DEV_HARDWARE_TYPE_MODEM = 0;
    public static final int DEV_HARDWARE_TYPE_SIM = 1;
    public static final int DEV_MODEM_RIL_MODEL_MULTIPLE = 1;
    public static final int DEV_MODEM_RIL_MODEL_SINGLE = 0;
    static final String LOG_TAG = "HardwareConfig";
    public int maxActiveDataCall;
    public int maxActiveVoiceCall;
    public int maxStandby;
    public String modemUuid;
    public BitSet rat;
    public int rilModel;
    public int state;
    public int type;
    public String uuid;

    public HardwareConfig(int i) {
    }

    public HardwareConfig(String str) {
        String[] split = str.split(",");
        this.type = Integer.parseInt(split[0]);
        switch (this.type) {
            case 0:
                assignModem(split[1].trim(), Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]), Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]));
                return;
            case 1:
                assignSim(split[1].trim(), Integer.parseInt(split[2]), split[3].trim());
                return;
            default:
                return;
        }
    }

    public void assignModem(String str, int i, int i2, int i3, int i4, int i5, int i6) {
        if (this.type == 0) {
            char[] toCharArray = Integer.toBinaryString(i3).toCharArray();
            this.uuid = str;
            this.rilModel = i2;
            this.rat = new BitSet(toCharArray.length);
            for (int i7 = 0; i7 < toCharArray.length; i7++) {
                this.rat.set(i7, toCharArray[i7] == '1');
            }
            this.maxActiveVoiceCall = i4;
            this.maxActiveDataCall = i5;
            this.maxStandby = i6;
        }
    }

    public void assignSim(String str, int i, String str2) {
        if (this.type == 1) {
            this.uuid = str;
            this.modemUuid = str2;
        }
    }

    public int compareTo(HardwareConfig hardwareConfig) {
        return toString().compareTo(hardwareConfig.toString());
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (this.type == 0) {
            stringBuilder.append("Modem ");
            stringBuilder.append("{ uuid=" + this.uuid);
            stringBuilder.append(", state=" + this.state);
            stringBuilder.append(", rilModel=" + this.rilModel);
            stringBuilder.append(", rat=" + this.rat.toString());
            stringBuilder.append(", maxActiveVoiceCall=" + this.maxActiveVoiceCall);
            stringBuilder.append(", maxActiveDataCall=" + this.maxActiveDataCall);
            stringBuilder.append(", maxStandby=" + this.maxStandby);
            stringBuilder.append(" }");
        } else if (this.type == 1) {
            stringBuilder.append("Sim ");
            stringBuilder.append("{ uuid=" + this.uuid);
            stringBuilder.append(", modemUuid=" + this.modemUuid);
            stringBuilder.append(", state=" + this.state);
            stringBuilder.append(" }");
        } else {
            stringBuilder.append("Invalid Configration");
        }
        return stringBuilder.toString();
    }
}
