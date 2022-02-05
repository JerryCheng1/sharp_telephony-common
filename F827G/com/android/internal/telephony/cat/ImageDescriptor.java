package com.android.internal.telephony.cat;

import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ImageDescriptor {
    static final int CODING_SCHEME_BASIC = 17;
    static final int CODING_SCHEME_COLOUR = 33;
    int mWidth = 0;
    int mHeight = 0;
    int mCodingScheme = 0;
    int mImageId = 0;
    int mHighOffset = 0;
    int mLowOffset = 0;
    int mLength = 0;

    ImageDescriptor() {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static ImageDescriptor parse(byte[] rawData, int valueIndex) {
        int valueIndex2;
        ImageDescriptor d = new ImageDescriptor();
        int valueIndex3 = valueIndex + 1;
        try {
            d.mWidth = rawData[valueIndex] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            valueIndex2 = valueIndex3 + 1;
        } catch (IndexOutOfBoundsException e) {
        }
        try {
            d.mHeight = rawData[valueIndex3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int valueIndex4 = valueIndex2 + 1;
            d.mCodingScheme = rawData[valueIndex2] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int valueIndex5 = valueIndex4 + 1;
            d.mImageId = (rawData[valueIndex4] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8;
            int valueIndex6 = valueIndex5 + 1;
            d.mImageId |= rawData[valueIndex5] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int valueIndex7 = valueIndex6 + 1;
            d.mHighOffset = rawData[valueIndex6] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int valueIndex8 = valueIndex7 + 1;
            d.mLowOffset = rawData[valueIndex7] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int valueIndex9 = valueIndex8 + 1;
            int i = (rawData[valueIndex8] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8;
            valueIndex3 = valueIndex9 + 1;
            d.mLength = i | (rawData[valueIndex9] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
            CatLog.d("ImageDescriptor", "parse; Descriptor : " + d.mWidth + ", " + d.mHeight + ", " + d.mCodingScheme + ", 0x" + Integer.toHexString(d.mImageId) + ", " + d.mHighOffset + ", " + d.mLowOffset + ", " + d.mLength);
            return d;
        } catch (IndexOutOfBoundsException e2) {
            CatLog.d("ImageDescriptor", "parse; failed parsing image descriptor");
            return null;
        }
    }
}
