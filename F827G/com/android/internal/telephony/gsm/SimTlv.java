package com.android.internal.telephony.gsm;

import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SimTlv {
    int mCurDataLength;
    int mCurDataOffset;
    int mCurOffset;
    boolean mHasValidTlvObject = parseCurrentTlvObject();
    byte[] mRecord;
    int mTlvLength;
    int mTlvOffset;

    public SimTlv(byte[] record, int offset, int length) {
        this.mRecord = record;
        this.mTlvOffset = offset;
        this.mTlvLength = length;
        this.mCurOffset = offset;
    }

    public boolean nextObject() {
        if (!this.mHasValidTlvObject) {
            return false;
        }
        this.mCurOffset = this.mCurDataOffset + this.mCurDataLength;
        this.mHasValidTlvObject = parseCurrentTlvObject();
        return this.mHasValidTlvObject;
    }

    public boolean isValidObject() {
        return this.mHasValidTlvObject;
    }

    public int getTag() {
        if (!this.mHasValidTlvObject) {
            return 0;
        }
        return this.mRecord[this.mCurOffset] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
    }

    public byte[] getData() {
        if (!this.mHasValidTlvObject) {
            return null;
        }
        byte[] ret = new byte[this.mCurDataLength];
        System.arraycopy(this.mRecord, this.mCurDataOffset, ret, 0, this.mCurDataLength);
        return ret;
    }

    private boolean parseCurrentTlvObject() {
        try {
            if (this.mRecord[this.mCurOffset] == 0 || (this.mRecord[this.mCurOffset] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) == 255) {
                return false;
            }
            if ((this.mRecord[this.mCurOffset + 1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) < 128) {
                this.mCurDataLength = this.mRecord[this.mCurOffset + 1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                this.mCurDataOffset = this.mCurOffset + 2;
            } else if ((this.mRecord[this.mCurOffset + 1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) != 129) {
                return false;
            } else {
                this.mCurDataLength = this.mRecord[this.mCurOffset + 2] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                this.mCurDataOffset = this.mCurOffset + 3;
            }
            if (this.mCurDataLength + this.mCurDataOffset <= this.mTlvOffset + this.mTlvLength) {
                return true;
            }
            return false;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
