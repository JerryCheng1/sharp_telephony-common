package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.format.Time;
import com.android.internal.telephony.uicc.IccUtils;
import java.util.Arrays;

public class SmsCbEtwsInfo implements Parcelable {
    public static final Creator<SmsCbEtwsInfo> CREATOR = new Creator<SmsCbEtwsInfo>() {
        public SmsCbEtwsInfo createFromParcel(Parcel parcel) {
            return new SmsCbEtwsInfo(parcel);
        }

        public SmsCbEtwsInfo[] newArray(int i) {
            return new SmsCbEtwsInfo[i];
        }
    };
    public static final int ETWS_WARNING_TYPE_EARTHQUAKE = 0;
    public static final int ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI = 2;
    public static final int ETWS_WARNING_TYPE_OTHER_EMERGENCY = 4;
    public static final int ETWS_WARNING_TYPE_TEST_MESSAGE = 3;
    public static final int ETWS_WARNING_TYPE_TSUNAMI = 1;
    public static final int ETWS_WARNING_TYPE_UNKNOWN = -1;
    private final boolean mActivatePopup;
    private final boolean mEmergencyUserAlert;
    private final byte[] mWarningSecurityInformation;
    private final int mWarningType;

    public SmsCbEtwsInfo(int i, boolean z, boolean z2, byte[] bArr) {
        this.mWarningType = i;
        this.mEmergencyUserAlert = z;
        this.mActivatePopup = z2;
        this.mWarningSecurityInformation = bArr;
    }

    SmsCbEtwsInfo(Parcel parcel) {
        boolean z = true;
        this.mWarningType = parcel.readInt();
        this.mEmergencyUserAlert = parcel.readInt() != 0;
        if (parcel.readInt() == 0) {
            z = false;
        }
        this.mActivatePopup = z;
        this.mWarningSecurityInformation = parcel.createByteArray();
    }

    public int describeContents() {
        return 0;
    }

    public byte[] getPrimaryNotificationSignature() {
        return (this.mWarningSecurityInformation == null || this.mWarningSecurityInformation.length < 50) ? null : Arrays.copyOfRange(this.mWarningSecurityInformation, 7, 50);
    }

    public long getPrimaryNotificationTimestamp() {
        if (this.mWarningSecurityInformation == null || this.mWarningSecurityInformation.length < 7) {
            return 0;
        }
        int gsmBcdByteToInt = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[0]);
        int gsmBcdByteToInt2 = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[1]);
        int gsmBcdByteToInt3 = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[2]);
        int gsmBcdByteToInt4 = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[3]);
        int gsmBcdByteToInt5 = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[4]);
        int gsmBcdByteToInt6 = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[5]);
        byte b = this.mWarningSecurityInformation[6];
        int gsmBcdByteToInt7 = IccUtils.gsmBcdByteToInt((byte) (b & -9));
        if ((b & 8) != 0) {
            gsmBcdByteToInt7 = -gsmBcdByteToInt7;
        }
        Time time = new Time("UTC");
        time.year = gsmBcdByteToInt + 2000;
        time.month = gsmBcdByteToInt2 - 1;
        time.monthDay = gsmBcdByteToInt3;
        time.hour = gsmBcdByteToInt4;
        time.minute = gsmBcdByteToInt5;
        time.second = gsmBcdByteToInt6;
        return time.toMillis(true) - ((long) (((gsmBcdByteToInt7 * 15) * 60) * 1000));
    }

    public int getWarningType() {
        return this.mWarningType;
    }

    public boolean isEmergencyUserAlert() {
        return this.mEmergencyUserAlert;
    }

    public boolean isPopupAlert() {
        return this.mActivatePopup;
    }

    public String toString() {
        return "SmsCbEtwsInfo{warningType=" + this.mWarningType + ", emergencyUserAlert=" + this.mEmergencyUserAlert + ", activatePopup=" + this.mActivatePopup + '}';
    }

    public void writeToParcel(Parcel parcel, int i) {
        int i2 = 1;
        parcel.writeInt(this.mWarningType);
        parcel.writeInt(this.mEmergencyUserAlert ? 1 : 0);
        if (!this.mActivatePopup) {
            i2 = 0;
        }
        parcel.writeInt(i2);
        parcel.writeByteArray(this.mWarningSecurityInformation);
    }
}
