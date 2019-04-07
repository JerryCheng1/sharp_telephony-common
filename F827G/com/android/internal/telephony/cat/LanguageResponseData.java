package com.android.internal.telephony.cat;

import com.android.internal.telephony.GsmAlphabet;
import java.io.ByteArrayOutputStream;

class LanguageResponseData extends ResponseData {
    private String mLang;

    public LanguageResponseData(String str) {
        this.mLang = str;
    }

    public void format(ByteArrayOutputStream byteArrayOutputStream) {
        int i = 0;
        if (byteArrayOutputStream != null) {
            byteArrayOutputStream.write(ComprehensionTlvTag.LANGUAGE.value() | 128);
            byte[] stringToGsm8BitPacked = (this.mLang == null || this.mLang.length() <= 0) ? new byte[0] : GsmAlphabet.stringToGsm8BitPacked(this.mLang);
            byteArrayOutputStream.write(stringToGsm8BitPacked.length);
            int length = stringToGsm8BitPacked.length;
            while (i < length) {
                byteArrayOutputStream.write(stringToGsm8BitPacked[i]);
                i++;
            }
        }
    }
}
