package com.android.internal.telephony.uicc;

import com.google.android.mms.pdu.PduHeaders;

public class IccIoResult {
    public byte[] payload;
    public int sw1;
    public int sw2;

    public IccIoResult(int i, int i2, String str) {
        this(i, i2, IccUtils.hexStringToBytes(str));
    }

    public IccIoResult(int i, int i2, byte[] bArr) {
        this.sw1 = i;
        this.sw2 = i2;
        this.payload = bArr;
    }

    public IccException getException() {
        if (success()) {
            return null;
        }
        switch (this.sw1) {
            case 148:
                return this.sw2 == 8 ? new IccFileTypeMismatch() : new IccFileNotFound();
            default:
                return new IccException("sw1:" + this.sw1 + " sw2:" + this.sw2);
        }
    }

    public boolean success() {
        return this.sw1 == 144 || this.sw1 == 145 || this.sw1 == PduHeaders.REPLY_CHARGING_ID || this.sw1 == PduHeaders.REPLY_CHARGING_SIZE;
    }

    public String toString() {
        return "IccIoResponse sw1:0x" + Integer.toHexString(this.sw1) + " sw2:0x" + Integer.toHexString(this.sw2);
    }
}
