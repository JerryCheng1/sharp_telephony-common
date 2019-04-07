package com.android.internal.telephony.gsm;

public class SimTlv {
    int mCurDataLength;
    int mCurDataOffset;
    int mCurOffset;
    boolean mHasValidTlvObject = parseCurrentTlvObject();
    byte[] mRecord;
    int mTlvLength;
    int mTlvOffset;

    public SimTlv(byte[] bArr, int i, int i2) {
        this.mRecord = bArr;
        this.mTlvOffset = i;
        this.mTlvLength = i2;
        this.mCurOffset = i;
    }

    private boolean parseCurrentTlvObject() {
        try {
            if (this.mRecord[this.mCurOffset] == (byte) 0 || (this.mRecord[this.mCurOffset] & 255) == 255) {
                return false;
            }
            if ((this.mRecord[this.mCurOffset + 1] & 255) < 128) {
                this.mCurDataLength = this.mRecord[this.mCurOffset + 1] & 255;
                this.mCurDataOffset = this.mCurOffset + 2;
            } else if ((this.mRecord[this.mCurOffset + 1] & 255) != 129) {
                return false;
            } else {
                this.mCurDataLength = this.mRecord[this.mCurOffset + 2] & 255;
                this.mCurDataOffset = this.mCurOffset + 3;
            }
            return this.mCurDataLength + this.mCurDataOffset <= this.mTlvOffset + this.mTlvLength;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    public byte[] getData() {
        if (!this.mHasValidTlvObject) {
            return null;
        }
        byte[] bArr = new byte[this.mCurDataLength];
        System.arraycopy(this.mRecord, this.mCurDataOffset, bArr, 0, this.mCurDataLength);
        return bArr;
    }

    public int getTag() {
        return !this.mHasValidTlvObject ? 0 : this.mRecord[this.mCurOffset] & 255;
    }

    public boolean isValidObject() {
        return this.mHasValidTlvObject;
    }

    public boolean nextObject() {
        if (!this.mHasValidTlvObject) {
            return false;
        }
        this.mCurOffset = this.mCurDataOffset + this.mCurDataLength;
        this.mHasValidTlvObject = parseCurrentTlvObject();
        return this.mHasValidTlvObject;
    }
}
