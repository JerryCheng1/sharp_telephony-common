package com.android.internal.telephony.cat;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

class GetInkeyInputResponseData extends ResponseData {
    protected static final byte GET_INKEY_NO = (byte) 0;
    protected static final byte GET_INKEY_YES = (byte) 1;
    public String mInData;
    private boolean mIsPacked;
    private boolean mIsUcs2;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;

    public GetInkeyInputResponseData(String str, boolean z, boolean z2) {
        this.mIsUcs2 = z;
        this.mIsPacked = z2;
        this.mInData = str;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean z) {
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = z;
    }

    public void format(ByteArrayOutputStream byteArrayOutputStream) {
        byte b = (byte) 1;
        int i = 0;
        if (byteArrayOutputStream != null) {
            byte[] bArr;
            int length;
            byteArrayOutputStream.write(ComprehensionTlvTag.TEXT_STRING.value() | 128);
            if (this.mIsYesNo) {
                byte[] bArr2 = new byte[1];
                if (!this.mYesNoResponse) {
                    b = GET_INKEY_NO;
                }
                bArr2[0] = b;
                bArr = bArr2;
            } else if (this.mInData == null || this.mInData.length() <= 0) {
                bArr = new byte[0];
            } else {
                try {
                    if (this.mIsUcs2) {
                        bArr = this.mInData.getBytes("UTF-16BE");
                    } else if (this.mIsPacked) {
                        length = this.mInData.length();
                        bArr = new byte[length];
                        System.arraycopy(GsmAlphabet.stringToGsm7BitPacked(this.mInData, 0, 0), 1, bArr, 0, length);
                    } else {
                        bArr = GsmAlphabet.stringToGsm8BitPacked(this.mInData);
                    }
                } catch (UnsupportedEncodingException e) {
                    bArr = new byte[0];
                } catch (EncodeException e2) {
                    bArr = new byte[0];
                }
            }
            ResponseData.writeLength(byteArrayOutputStream, bArr.length + 1);
            if (this.mIsUcs2) {
                byteArrayOutputStream.write(8);
            } else if (this.mIsPacked) {
                byteArrayOutputStream.write(0);
            } else {
                byteArrayOutputStream.write(4);
            }
            length = bArr.length;
            while (i < length) {
                byteArrayOutputStream.write(bArr[i]);
                i++;
            }
        }
    }
}
