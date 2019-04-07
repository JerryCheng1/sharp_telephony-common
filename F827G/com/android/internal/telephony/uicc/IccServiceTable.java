package com.android.internal.telephony.uicc;

import android.telephony.Rlog;

public abstract class IccServiceTable {
    protected final byte[] mServiceTable;

    protected IccServiceTable(byte[] bArr) {
        this.mServiceTable = bArr;
    }

    public abstract String getTag();

    public abstract Object[] getValues();

    /* Access modifiers changed, original: protected */
    public boolean isAvailable(int i) {
        int i2 = i / 8;
        if (i2 < this.mServiceTable.length) {
            return (this.mServiceTable[i2] & (1 << (i % 8))) != 0;
        } else {
            Rlog.e(getTag(), "isAvailable for service " + (i + 1) + " fails, max service is " + (this.mServiceTable.length * 8));
            return false;
        }
    }

    public String toString() {
        Object[] values = getValues();
        int length = this.mServiceTable.length;
        StringBuilder append = new StringBuilder(getTag()).append('[').append(length * 8).append("]={ ");
        int i = 0;
        for (int i2 = 0; i2 < length; i2++) {
            byte b = this.mServiceTable[i2];
            for (int i3 = 0; i3 < 8; i3++) {
                if (((1 << i3) & b) != 0) {
                    if (i != 0) {
                        append.append(", ");
                    } else {
                        i = 1;
                    }
                    int i4 = (i2 * 8) + i3;
                    if (i4 < values.length) {
                        append.append(values[i4]);
                    } else {
                        append.append('#').append(i4 + 1);
                    }
                }
            }
        }
        return append.append(" }").toString();
    }
}
