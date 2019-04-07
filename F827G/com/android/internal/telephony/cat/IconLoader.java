package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.HashMap;

class IconLoader extends Handler {
    private static final int CLUT_ENTRY_SIZE = 3;
    private static final int CLUT_LOCATION_OFFSET = 4;
    private static final int EVENT_READ_CLUT_DONE = 3;
    private static final int EVENT_READ_EF_IMG_RECOED_DONE = 1;
    private static final int EVENT_READ_ICON_DONE = 2;
    private static final int STATE_MULTI_ICONS = 2;
    private static final int STATE_SINGLE_ICON = 1;
    private static IconLoader sLoader = null;
    private static HandlerThread sThread = null;
    private Bitmap mCurrentIcon = null;
    private int mCurrentRecordIndex = 0;
    private Message mEndMsg = null;
    private byte[] mIconData = null;
    private Bitmap[] mIcons = null;
    private HashMap<Integer, Bitmap> mIconsCache = null;
    private ImageDescriptor mId = null;
    private int mRecordNumber;
    private int[] mRecordNumbers = null;
    private IccFileHandler mSimFH = null;
    private int mState = 1;

    private IconLoader(Looper looper, IccFileHandler iccFileHandler) {
        super(looper);
        this.mSimFH = iccFileHandler;
        this.mIconsCache = new HashMap(50);
    }

    private static int bitToBnW(int i) {
        return i == 1 ? -1 : -16777216;
    }

    static IconLoader getInstance(Handler handler, IccFileHandler iccFileHandler) {
        if (sLoader != null) {
            return sLoader;
        }
        if (iccFileHandler == null) {
            return null;
        }
        sThread = new HandlerThread("Cat Icon Loader");
        sThread.start();
        return new IconLoader(sThread.getLooper(), iccFileHandler);
    }

    private static int getMask(int i) {
        switch (i) {
            case 1:
                return 1;
            case 2:
                return 3;
            case 3:
                return 7;
            case 4:
                return 15;
            case 5:
                return 31;
            case 6:
                return 63;
            case 7:
                return 127;
            case 8:
                return 255;
            default:
                return 0;
        }
    }

    private boolean handleImageDescriptor(byte[] bArr) {
        this.mId = ImageDescriptor.parse(bArr, 1);
        return this.mId != null;
    }

    public static Bitmap parseToBnW(byte[] bArr, int i) {
        int i2 = bArr[0] & 255;
        int i3 = bArr[1] & 255;
        int i4 = i2 * i3;
        int[] iArr = new int[i4];
        int i5 = 2;
        int i6 = 0;
        int i7 = 0;
        int i8 = 7;
        while (i7 < i4) {
            int i9;
            if (i7 % 8 == 0) {
                i9 = i5 + 1;
                i6 = bArr[i5];
                i8 = 7;
            } else {
                i9 = i5;
            }
            iArr[i7] = bitToBnW((i6 >> i8) & 1);
            i8--;
            i7++;
            i5 = i9;
        }
        if (i7 != i4) {
            CatLog.d("IconLoader", "parseToBnW; size error");
        }
        return Bitmap.createBitmap(iArr, i2, i3, Config.ARGB_8888);
    }

    public static Bitmap parseToRGB(byte[] bArr, int i, boolean z, byte[] bArr2) {
        int i2 = bArr[0] & 255;
        int i3 = bArr[1] & 255;
        int i4 = bArr[2] & 255;
        byte b = bArr[3];
        if (true == z) {
            bArr2[(b & 255) - 1] = (byte) 0;
        }
        int i5 = i2 * i3;
        int[] iArr = new int[i5];
        int i6 = 8 - i4;
        int i7 = 7;
        byte b2 = bArr[6];
        int mask = getMask(i4);
        Object obj = 8 % i4 == 0 ? 1 : null;
        int i8 = 0;
        int i9 = i6;
        while (i8 < i5) {
            int i10;
            if (i9 < 0) {
                i10 = i7 + 1;
                b2 = bArr[i7];
                i9 = obj != null ? i6 : i9 * -1;
            } else {
                i10 = i7;
            }
            i7 = ((b2 >> i9) & mask) * 3;
            iArr[i8] = Color.rgb(bArr2[i7], bArr2[i7 + 1], bArr2[i7 + 2]);
            i9 -= i4;
            i8++;
            i7 = i10;
        }
        return Bitmap.createBitmap(iArr, i2, i3, Config.ARGB_8888);
    }

    private void postIcon() {
        if (this.mState == 1) {
            this.mEndMsg.obj = this.mCurrentIcon;
            this.mEndMsg.sendToTarget();
        } else if (this.mState == 2) {
            Bitmap[] bitmapArr = this.mIcons;
            int i = this.mCurrentRecordIndex;
            this.mCurrentRecordIndex = i + 1;
            bitmapArr[i] = this.mCurrentIcon;
            if (this.mCurrentRecordIndex < this.mRecordNumbers.length) {
                startLoadingIcon(this.mRecordNumbers[this.mCurrentRecordIndex]);
                return;
            }
            this.mEndMsg.obj = this.mIcons;
            this.mEndMsg.sendToTarget();
        }
    }

    private void readClut() {
        byte b = this.mIconData[3];
        this.mSimFH.loadEFImgTransparent(this.mId.mImageId, this.mIconData[4], this.mIconData[5], b * 3, obtainMessage(3));
    }

    private void readIconData() {
        Message obtainMessage = obtainMessage(2);
        this.mSimFH.loadEFImgTransparent(this.mId.mImageId, 0, 0, this.mId.mLength, obtainMessage);
    }

    private void readId() {
        if (this.mRecordNumber < 0) {
            this.mCurrentIcon = null;
            postIcon();
            return;
        }
        this.mSimFH.loadEFImgLinearFixed(this.mRecordNumber, obtainMessage(1));
    }

    private void startLoadingIcon(int i) {
        this.mId = null;
        this.mIconData = null;
        this.mCurrentIcon = null;
        this.mRecordNumber = i;
        if (this.mIconsCache.containsKey(Integer.valueOf(i))) {
            this.mCurrentIcon = (Bitmap) this.mIconsCache.get(Integer.valueOf(i));
            postIcon();
            return;
        }
        readId();
    }

    public void dispose() {
        this.mSimFH = null;
        if (sThread != null) {
            sThread.quit();
            sThread = null;
        }
        this.mIconsCache = null;
        sLoader = null;
    }

    public void handleMessage(Message message) {
        try {
            switch (message.what) {
                case 1:
                    if (handleImageDescriptor((byte[]) ((AsyncResult) message.obj).result)) {
                        readIconData();
                        return;
                    }
                    throw new Exception("Unable to parse image descriptor");
                case 2:
                    CatLog.d((Object) this, "load icon done");
                    byte[] bArr = (byte[]) ((AsyncResult) message.obj).result;
                    if (this.mId.mCodingScheme == 17) {
                        this.mCurrentIcon = parseToBnW(bArr, bArr.length);
                        this.mIconsCache.put(Integer.valueOf(this.mRecordNumber), this.mCurrentIcon);
                        postIcon();
                        return;
                    } else if (this.mId.mCodingScheme == 33) {
                        this.mIconData = bArr;
                        readClut();
                        return;
                    } else {
                        CatLog.d((Object) this, "else  /postIcon ");
                        postIcon();
                        return;
                    }
                case 3:
                    this.mCurrentIcon = parseToRGB(this.mIconData, this.mIconData.length, false, (byte[]) ((AsyncResult) message.obj).result);
                    this.mIconsCache.put(Integer.valueOf(this.mRecordNumber), this.mCurrentIcon);
                    postIcon();
                    return;
                default:
                    return;
            }
        } catch (Exception e) {
            CatLog.d((Object) this, "Icon load failed");
            postIcon();
        }
        CatLog.d((Object) this, "Icon load failed");
        postIcon();
    }

    /* Access modifiers changed, original: 0000 */
    public void loadIcon(int i, Message message) {
        if (message != null) {
            this.mEndMsg = message;
            this.mState = 1;
            startLoadingIcon(i);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void loadIcons(int[] iArr, Message message) {
        if (iArr != null && iArr.length != 0 && message != null) {
            this.mEndMsg = message;
            this.mIcons = new Bitmap[iArr.length];
            this.mRecordNumbers = iArr;
            this.mCurrentRecordIndex = 0;
            this.mState = 2;
            startLoadingIcon(iArr[0]);
        }
    }
}
