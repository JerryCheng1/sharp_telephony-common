package com.android.internal.telephony.cat;

public class ImageDescriptor {
    static final int CODING_SCHEME_BASIC = 17;
    static final int CODING_SCHEME_COLOUR = 33;
    int mCodingScheme = 0;
    int mHeight = 0;
    int mHighOffset = 0;
    int mImageId = 0;
    int mLength = 0;
    int mLowOffset = 0;
    int mWidth = 0;

    ImageDescriptor() {
    }

    static ImageDescriptor parse(byte[] bArr, int i) {
        ImageDescriptor imageDescriptor = new ImageDescriptor();
        int i2 = i + 1;
        try {
            imageDescriptor.mWidth = bArr[i] & 255;
            int i3 = i2 + 1;
            try {
                imageDescriptor.mHeight = bArr[i2] & 255;
                i2 = i3 + 1;
                imageDescriptor.mCodingScheme = bArr[i3] & 255;
                i3 = i2 + 1;
                imageDescriptor.mImageId = (bArr[i2] & 255) << 8;
                int i4 = i3 + 1;
                imageDescriptor.mImageId |= bArr[i3] & 255;
                i2 = i4 + 1;
                imageDescriptor.mHighOffset = bArr[i4] & 255;
                i3 = i2 + 1;
                imageDescriptor.mLowOffset = bArr[i2] & 255;
                i2 = i3 + 1;
                i4 = i2 + 1;
                imageDescriptor.mLength = (bArr[i2] & 255) | ((bArr[i3] & 255) << 8);
                CatLog.d("ImageDescriptor", "parse; Descriptor : " + imageDescriptor.mWidth + ", " + imageDescriptor.mHeight + ", " + imageDescriptor.mCodingScheme + ", 0x" + Integer.toHexString(imageDescriptor.mImageId) + ", " + imageDescriptor.mHighOffset + ", " + imageDescriptor.mLowOffset + ", " + imageDescriptor.mLength);
                return imageDescriptor;
            } catch (IndexOutOfBoundsException e) {
                CatLog.d("ImageDescriptor", "parse; failed parsing image descriptor");
                return null;
            }
        } catch (IndexOutOfBoundsException e2) {
        }
    }
}
