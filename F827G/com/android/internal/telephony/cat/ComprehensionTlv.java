package com.android.internal.telephony.cat;

import android.telephony.Rlog;
import com.android.internal.telephony.WapPushManagerParams;
import java.util.ArrayList;
import java.util.List;

class ComprehensionTlv {
    private static final String LOG_TAG = "ComprehensionTlv";
    private boolean mCr;
    private int mLength;
    private byte[] mRawValue;
    private int mTag;
    private int mValueIndex;

    protected ComprehensionTlv(int i, boolean z, int i2, byte[] bArr, int i3) {
        this.mTag = i;
        this.mCr = z;
        this.mLength = i2;
        this.mValueIndex = i3;
        this.mRawValue = bArr;
    }

    public static ComprehensionTlv decode(byte[] bArr, int i) throws ResultException {
        int i2;
        boolean z;
        int i3;
        int i4;
        boolean z2 = false;
        int length = bArr.length;
        int i5 = i + 1;
        int i6 = bArr[i] & 255;
        switch (i6) {
            case 0:
            case 128:
            case 255:
                try {
                    Rlog.d("CAT     ", "decode: unexpected first tag byte=" + Integer.toHexString(i6) + ", startIndex=" + i + " curIndex=" + i5 + " endIndex=" + length);
                    return null;
                } catch (IndexOutOfBoundsException e) {
                    i2 = i5;
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "IndexOutOfBoundsException startIndex=" + i + " curIndex=" + i2 + " endIndex=" + length);
                }
            case 127:
                int i7 = (bArr[i5 + 1] & 255) | ((bArr[i5] & 255) << 8);
                z = (WapPushManagerParams.FURTHER_PROCESSING & i7) != 0;
                i3 = i7 & -32769;
                i5 += 2;
                break;
            default:
                if ((i6 & 128) != 0) {
                    z2 = true;
                }
                i3 = i6 & -129;
                z = z2;
                break;
        }
        i2 = i5 + 1;
        i5 = bArr[i5] & 255;
        if (i5 < 128) {
            i4 = i2;
        } else if (i5 == 129) {
            i4 = i2 + 1;
            i5 = bArr[i2] & 255;
            if (i5 < 128) {
                try {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "length < 0x80 length=" + Integer.toHexString(i5) + " startIndex=" + i + " curIndex=" + i4 + " endIndex=" + length);
                } catch (IndexOutOfBoundsException e2) {
                    i2 = i4;
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "IndexOutOfBoundsException startIndex=" + i + " curIndex=" + i2 + " endIndex=" + length);
                }
            }
        } else if (i5 == 130) {
            i5 = ((bArr[i2] & 255) << 8) | (bArr[i2 + 1] & 255);
            i4 = i2 + 2;
            if (i5 < 256) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "two byte length < 0x100 length=" + Integer.toHexString(i5) + " startIndex=" + i + " curIndex=" + i4 + " endIndex=" + length);
            }
        } else if (i5 == 131) {
            i5 = (((bArr[i2] & 255) << 16) | ((bArr[i2 + 1] & 255) << 8)) | (bArr[i2 + 2] & 255);
            i4 = i2 + 3;
            if (i5 < 65536) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "three byte length < 0x10000 length=0x" + Integer.toHexString(i5) + " startIndex=" + i + " curIndex=" + i4 + " endIndex=" + length);
            }
        } else {
            try {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Bad length modifer=" + i5 + " startIndex=" + i + " curIndex=" + i2 + " endIndex=" + length);
            } catch (IndexOutOfBoundsException e3) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "IndexOutOfBoundsException startIndex=" + i + " curIndex=" + i2 + " endIndex=" + length);
            }
        }
        try {
            return new ComprehensionTlv(i3, z, i5, bArr, i4);
        } catch (IndexOutOfBoundsException e4) {
            i2 = i4;
        }
    }

    public static List<ComprehensionTlv> decodeMany(byte[] bArr, int i) throws ResultException {
        ArrayList arrayList = new ArrayList();
        int length = bArr.length;
        while (i < length) {
            ComprehensionTlv decode = decode(bArr, i);
            if (decode == null) {
                CatLog.d(LOG_TAG, "decodeMany: ctlv is null, stop decoding");
                break;
            }
            arrayList.add(decode);
            i = decode.mValueIndex + decode.mLength;
        }
        return arrayList;
    }

    public int getLength() {
        return this.mLength;
    }

    public byte[] getRawValue() {
        return this.mRawValue;
    }

    public int getTag() {
        return this.mTag;
    }

    public int getValueIndex() {
        return this.mValueIndex;
    }

    public boolean isComprehensionRequired() {
        return this.mCr;
    }
}
