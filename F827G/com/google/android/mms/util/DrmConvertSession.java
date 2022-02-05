package com.google.android.mms.util;

import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.util.Log;
import com.google.android.mms.pdu.PduPart;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DrmConvertSession {
    private static final String TAG = "DrmConvertSession";
    private int mConvertSessionId;
    private DrmManagerClient mDrmClient;

    private DrmConvertSession(DrmManagerClient drmClient, int convertSessionId) {
        this.mDrmClient = drmClient;
        this.mConvertSessionId = convertSessionId;
    }

    /* JADX WARN: Removed duplicated region for block: B:11:0x001a A[ADDED_TO_REGION] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public static com.google.android.mms.util.DrmConvertSession open(android.content.Context r7, java.lang.String r8) {
        /*
            r1 = 0
            r0 = -1
            if (r7 == 0) goto L_0x0018
            if (r8 == 0) goto L_0x0018
            java.lang.String r4 = ""
            boolean r4 = r8.equals(r4)
            if (r4 != 0) goto L_0x0018
            android.drm.DrmManagerClient r2 = new android.drm.DrmManagerClient     // Catch: IllegalArgumentException -> 0x0063, IllegalStateException -> 0x0061
            r2.<init>(r7)     // Catch: IllegalArgumentException -> 0x0063, IllegalStateException -> 0x0061
            int r0 = r2.openConvertSession(r8)     // Catch: IllegalArgumentException -> 0x001e, IllegalStateException -> 0x0048
        L_0x0017:
            r1 = r2
        L_0x0018:
            if (r1 == 0) goto L_0x001c
            if (r0 >= 0) goto L_0x005b
        L_0x001c:
            r4 = 0
        L_0x001d:
            return r4
        L_0x001e:
            r3 = move-exception
            java.lang.String r4 = "DrmConvertSession"
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            r5.<init>()     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            java.lang.String r6 = "Conversion of Mimetype: "
            java.lang.StringBuilder r5 = r5.append(r6)     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            java.lang.StringBuilder r5 = r5.append(r8)     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            java.lang.String r6 = " is not supported."
            java.lang.StringBuilder r5 = r5.append(r6)     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            java.lang.String r5 = r5.toString()     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            android.util.Log.w(r4, r5, r3)     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            goto L_0x0017
        L_0x003e:
            r3 = move-exception
            r1 = r2
        L_0x0040:
            java.lang.String r4 = "DrmConvertSession"
            java.lang.String r5 = "DrmManagerClient instance could not be created, context is Illegal."
            android.util.Log.w(r4, r5)
            goto L_0x0018
        L_0x0048:
            r3 = move-exception
            java.lang.String r4 = "DrmConvertSession"
            java.lang.String r5 = "Could not access Open DrmFramework."
            android.util.Log.w(r4, r5, r3)     // Catch: IllegalArgumentException -> 0x003e, IllegalStateException -> 0x0051
            goto L_0x0017
        L_0x0051:
            r3 = move-exception
            r1 = r2
        L_0x0053:
            java.lang.String r4 = "DrmConvertSession"
            java.lang.String r5 = "DrmManagerClient didn't initialize properly."
            android.util.Log.w(r4, r5)
            goto L_0x0018
        L_0x005b:
            com.google.android.mms.util.DrmConvertSession r4 = new com.google.android.mms.util.DrmConvertSession
            r4.<init>(r1, r0)
            goto L_0x001d
        L_0x0061:
            r3 = move-exception
            goto L_0x0053
        L_0x0063:
            r3 = move-exception
            goto L_0x0040
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.util.DrmConvertSession.open(android.content.Context, java.lang.String):com.google.android.mms.util.DrmConvertSession");
    }

    public byte[] convert(byte[] inBuffer, int size) {
        DrmConvertedStatus convertedStatus;
        byte[] result = null;
        if (inBuffer != null) {
            try {
                if (size != inBuffer.length) {
                    byte[] buf = new byte[size];
                    System.arraycopy(inBuffer, 0, buf, 0, size);
                    convertedStatus = this.mDrmClient.convertData(this.mConvertSessionId, buf);
                } else {
                    convertedStatus = this.mDrmClient.convertData(this.mConvertSessionId, inBuffer);
                }
                if (convertedStatus == null || convertedStatus.statusCode != 1 || convertedStatus.convertedData == null) {
                    return null;
                }
                result = convertedStatus.convertedData;
                return result;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Buffer with data to convert is illegal. Convertsession: " + this.mConvertSessionId, e);
                return result;
            } catch (IllegalStateException e2) {
                Log.w(TAG, "Could not convert data. Convertsession: " + this.mConvertSessionId, e2);
                return result;
            }
        } else {
            throw new IllegalArgumentException("Parameter inBuffer is null");
        }
    }

    public int close(String filename) {
        Throwable th;
        RandomAccessFile rndAccessFile;
        SecurityException e;
        IllegalArgumentException e2;
        IOException e3;
        FileNotFoundException e4;
        RandomAccessFile rndAccessFile2;
        int result = 491;
        if (this.mDrmClient == null || this.mConvertSessionId < 0) {
            return 491;
        }
        try {
            DrmConvertedStatus convertedStatus = this.mDrmClient.closeConvertSession(this.mConvertSessionId);
            if (convertedStatus == null || convertedStatus.statusCode != 1 || convertedStatus.convertedData == null) {
                return 406;
            }
            try {
                rndAccessFile = null;
                try {
                    rndAccessFile2 = new RandomAccessFile(filename, "rw");
                } catch (FileNotFoundException e5) {
                    e4 = e5;
                } catch (IOException e6) {
                    e3 = e6;
                } catch (IllegalArgumentException e7) {
                    e2 = e7;
                } catch (SecurityException e8) {
                    e = e8;
                }
            } catch (Throwable th2) {
                th = th2;
            }
            try {
                rndAccessFile2.seek(convertedStatus.offset);
                rndAccessFile2.write(convertedStatus.convertedData);
                result = PduPart.P_CONTENT_TRANSFER_ENCODING;
                if (rndAccessFile2 != null) {
                    try {
                        rndAccessFile2.close();
                    } catch (IOException e9) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e9);
                    }
                }
            } catch (FileNotFoundException e10) {
                e4 = e10;
                rndAccessFile = rndAccessFile2;
                result = 492;
                Log.w(TAG, "File: " + filename + " could not be found.", e4);
                if (rndAccessFile != null) {
                    try {
                        rndAccessFile.close();
                    } catch (IOException e11) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e11);
                    }
                }
                return result;
            } catch (IOException e12) {
                e3 = e12;
                rndAccessFile = rndAccessFile2;
                result = 492;
                Log.w(TAG, "Could not access File: " + filename + " .", e3);
                if (rndAccessFile != null) {
                    try {
                        rndAccessFile.close();
                    } catch (IOException e13) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e13);
                    }
                }
                return result;
            } catch (IllegalArgumentException e14) {
                e2 = e14;
                rndAccessFile = rndAccessFile2;
                result = 492;
                Log.w(TAG, "Could not open file in mode: rw", e2);
                if (rndAccessFile != null) {
                    try {
                        rndAccessFile.close();
                    } catch (IOException e15) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e15);
                    }
                }
                return result;
            } catch (SecurityException e16) {
                e = e16;
                rndAccessFile = rndAccessFile2;
                Log.w(TAG, "Access to File: " + filename + " was denied denied by SecurityManager.", e);
                if (rndAccessFile != null) {
                    try {
                        rndAccessFile.close();
                    } catch (IOException e17) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e17);
                    }
                }
                return result;
            } catch (Throwable th3) {
                th = th3;
                rndAccessFile = rndAccessFile2;
                if (rndAccessFile != null) {
                    try {
                        rndAccessFile.close();
                    } catch (IOException e18) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e18);
                    }
                }
                throw th;
            }
            return result;
        } catch (IllegalStateException e19) {
            Log.w(TAG, "Could not close convertsession. Convertsession: " + this.mConvertSessionId, e19);
            return result;
        }
    }
}
