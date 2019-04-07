package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.provider.Telephony.Carriers;

public class OperatorInfo implements Parcelable {
    public static final Creator<OperatorInfo> CREATOR = new Creator<OperatorInfo>() {
        public OperatorInfo createFromParcel(Parcel parcel) {
            return new OperatorInfo(parcel.readString(), parcel.readString(), parcel.readString(), (State) parcel.readSerializable());
        }

        public OperatorInfo[] newArray(int i) {
            return new OperatorInfo[i];
        }
    };
    private String mOperatorAlphaLong;
    private String mOperatorAlphaShort;
    private String mOperatorNumeric;
    private String mRadioTech;
    private State mState;

    public enum State {
        UNKNOWN,
        AVAILABLE,
        CURRENT,
        FORBIDDEN
    }

    OperatorInfo(String str, String str2, String str3, State state) {
        this.mState = State.UNKNOWN;
        this.mRadioTech = "";
        this.mOperatorAlphaLong = str;
        this.mOperatorAlphaShort = str2;
        this.mOperatorNumeric = str3;
        this.mRadioTech = "";
        if (str3 != null) {
            String[] split = str3.split("\\+");
            this.mOperatorNumeric = split[0];
            if (split.length > 1) {
                this.mRadioTech = split[1];
            }
        }
        this.mState = state;
    }

    public OperatorInfo(String str, String str2, String str3, String str4) {
        this(str, str2, str3, rilStateToState(str4));
    }

    private static State rilStateToState(String str) {
        if (str.equals("unknown")) {
            return State.UNKNOWN;
        }
        if (str.equals("available")) {
            return State.AVAILABLE;
        }
        if (str.equals(Carriers.CURRENT)) {
            return State.CURRENT;
        }
        if (str.equals("forbidden")) {
            return State.FORBIDDEN;
        }
        throw new RuntimeException("RIL impl error: Invalid network state '" + str + "'");
    }

    public int describeContents() {
        return 0;
    }

    public String getOperatorAlphaLong() {
        return this.mOperatorAlphaLong;
    }

    public String getOperatorAlphaShort() {
        return this.mOperatorAlphaShort;
    }

    public String getOperatorNumeric() {
        return this.mOperatorNumeric;
    }

    public String getRadioTech() {
        return this.mRadioTech;
    }

    public State getState() {
        return this.mState;
    }

    public String toString() {
        return "OperatorInfo " + this.mOperatorAlphaLong + "/" + this.mOperatorAlphaShort + "/" + this.mOperatorNumeric + "/" + this.mState + "/" + this.mRadioTech;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.mOperatorAlphaLong);
        parcel.writeString(this.mOperatorAlphaShort);
        parcel.writeString(this.mOperatorNumeric + "+" + this.mRadioTech);
        parcel.writeSerializable(this.mState);
    }
}
