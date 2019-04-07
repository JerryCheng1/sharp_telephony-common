package com.google.android.mms.util;

import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.util.Log;

public class DrmConvertSession {
    private static final String TAG = "DrmConvertSession";
    private int mConvertSessionId;
    private DrmManagerClient mDrmClient;

    private DrmConvertSession(DrmManagerClient drmManagerClient, int i) {
        this.mDrmClient = drmManagerClient;
        this.mConvertSessionId = i;
    }

    /* JADX WARNING: Removed duplicated region for block: B:27:? A:{SYNTHETIC, RETURN, ORIG_RETURN, SKIP} */
    /* JADX WARNING: Removed duplicated region for block: B:10:0x0019 A:{SKIP} */
    /* JADX WARNING: Removed duplicated region for block: B:10:0x0019 A:{SKIP} */
    /* JADX WARNING: Removed duplicated region for block: B:27:? A:{SYNTHETIC, RETURN, ORIG_RETURN, SKIP} */
    public static com.google.android.mms.util.DrmConvertSession open(android.content.Context r7, java.lang.String r8) {
        /*
        r2 = 0;
        r0 = -1;
        if (r7 == 0) goto L_0x0063;
    L_0x0004:
        if (r8 == 0) goto L_0x0063;
    L_0x0006:
        r1 = "";
        r1 = r8.equals(r1);
        if (r1 != 0) goto L_0x0063;
    L_0x000e:
        r1 = new android.drm.DrmManagerClient;	 Catch:{ IllegalArgumentException -> 0x0060, IllegalStateException -> 0x005d }
        r1.<init>(r7);	 Catch:{ IllegalArgumentException -> 0x0060, IllegalStateException -> 0x005d }
        r0 = r1.openConvertSession(r8);	 Catch:{ IllegalArgumentException -> 0x001c, IllegalStateException -> 0x0045 }
    L_0x0017:
        if (r1 == 0) goto L_0x001b;
    L_0x0019:
        if (r0 >= 0) goto L_0x0057;
    L_0x001b:
        return r2;
    L_0x001c:
        r3 = move-exception;
        r4 = "DrmConvertSession";
        r5 = new java.lang.StringBuilder;	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        r5.<init>();	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        r6 = "Conversion of Mimetype: ";
        r5 = r5.append(r6);	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        r5 = r5.append(r8);	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        r6 = " is not supported.";
        r5 = r5.append(r6);	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        r5 = r5.toString();	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        android.util.Log.w(r4, r5, r3);	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        goto L_0x0017;
    L_0x003c:
        r3 = move-exception;
    L_0x003d:
        r3 = "DrmConvertSession";
        r4 = "DrmManagerClient instance could not be created, context is Illegal.";
        android.util.Log.w(r3, r4);
        goto L_0x0017;
    L_0x0045:
        r3 = move-exception;
        r4 = "DrmConvertSession";
        r5 = "Could not access Open DrmFramework.";
        android.util.Log.w(r4, r5, r3);	 Catch:{ IllegalArgumentException -> 0x003c, IllegalStateException -> 0x004e }
        goto L_0x0017;
    L_0x004e:
        r3 = move-exception;
    L_0x004f:
        r3 = "DrmConvertSession";
        r4 = "DrmManagerClient didn't initialize properly.";
        android.util.Log.w(r3, r4);
        goto L_0x0017;
    L_0x0057:
        r2 = new com.google.android.mms.util.DrmConvertSession;
        r2.<init>(r1, r0);
        goto L_0x001b;
    L_0x005d:
        r1 = move-exception;
        r1 = r2;
        goto L_0x004f;
    L_0x0060:
        r1 = move-exception;
        r1 = r2;
        goto L_0x003d;
    L_0x0063:
        r1 = r2;
        goto L_0x0017;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.util.DrmConvertSession.open(android.content.Context, java.lang.String):com.google.android.mms.util.DrmConvertSession");
    }

    /* JADX WARNING: Unknown top exception splitter block from list: {B:39:0x00c5=Splitter:B:39:0x00c5, B:49:0x010d=Splitter:B:49:0x010d, B:29:0x007e=Splitter:B:29:0x007e} */
    /* JADX WARNING: Removed duplicated region for block: B:95:0x01d8  */
    /* JADX WARNING: Removed duplicated region for block: B:72:0x018c A:{SYNTHETIC, Splitter:B:72:0x018c} */
    /* JADX WARNING: Removed duplicated region for block: B:72:0x018c A:{SYNTHETIC, Splitter:B:72:0x018c} */
    /* JADX WARNING: Removed duplicated region for block: B:95:0x01d8  */
    /* JADX WARNING: Removed duplicated region for block: B:32:0x009e A:{SYNTHETIC, Splitter:B:32:0x009e} */
    /* JADX WARNING: Removed duplicated region for block: B:42:0x00e5 A:{SYNTHETIC, Splitter:B:42:0x00e5} */
    /* JADX WARNING: Removed duplicated region for block: B:52:0x0116 A:{SYNTHETIC, Splitter:B:52:0x0116} */
    /* JADX WARNING: Removed duplicated region for block: B:62:0x015e A:{SYNTHETIC, Splitter:B:62:0x015e} */
    /* JADX WARNING: Removed duplicated region for block: B:95:0x01d8  */
    /* JADX WARNING: Removed duplicated region for block: B:72:0x018c A:{SYNTHETIC, Splitter:B:72:0x018c} */
    /* JADX WARNING: Removed duplicated region for block: B:91:0x01cd A:{ExcHandler: IllegalStateException (r1_12 'e' java.lang.Throwable), PHI: r0 , Splitter:B:18:0x003a} */
    /* JADX WARNING: Exception block dominator not found, dom blocks: [B:18:0x003a, B:72:0x018c] */
    /* JADX WARNING: Missing block: B:77:0x0191, code skipped:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:78:0x0192, code skipped:
            android.util.Log.w(TAG, "Failed to close File:" + r9 + ".", r0);
     */
    /* JADX WARNING: Missing block: B:91:0x01cd, code skipped:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:92:0x01ce, code skipped:
            r3 = r1;
            r2 = r0;
     */
    public int close(java.lang.String r9) {
        /*
        r8 = this;
        r1 = 491; // 0x1eb float:6.88E-43 double:2.426E-321;
        r0 = 200; // 0xc8 float:2.8E-43 double:9.9E-322;
        r4 = 0;
        r2 = 492; // 0x1ec float:6.9E-43 double:2.43E-321;
        r3 = r8.mDrmClient;
        if (r3 == 0) goto L_0x01d5;
    L_0x000b:
        r3 = r8.mConvertSessionId;
        if (r3 < 0) goto L_0x01d5;
    L_0x000f:
        r3 = r8.mDrmClient;	 Catch:{ IllegalStateException -> 0x01c8 }
        r5 = r8.mConvertSessionId;	 Catch:{ IllegalStateException -> 0x01c8 }
        r5 = r3.closeConvertSession(r5);	 Catch:{ IllegalStateException -> 0x01c8 }
        if (r5 == 0) goto L_0x0022;
    L_0x0019:
        r3 = r5.statusCode;	 Catch:{ IllegalStateException -> 0x01c8 }
        r6 = 1;
        if (r3 != r6) goto L_0x0022;
    L_0x001e:
        r3 = r5.convertedData;	 Catch:{ IllegalStateException -> 0x01c8 }
        if (r3 != 0) goto L_0x0026;
    L_0x0022:
        r0 = 406; // 0x196 float:5.69E-43 double:2.006E-321;
    L_0x0024:
        r2 = r0;
    L_0x0025:
        return r2;
    L_0x0026:
        r3 = new java.io.RandomAccessFile;	 Catch:{ FileNotFoundException -> 0x007c, IOException -> 0x00c3, IllegalArgumentException -> 0x010b, SecurityException -> 0x013c, all -> 0x0185 }
        r6 = "rw";
        r3.<init>(r9, r6);	 Catch:{ FileNotFoundException -> 0x007c, IOException -> 0x00c3, IllegalArgumentException -> 0x010b, SecurityException -> 0x013c, all -> 0x0185 }
        r4 = r5.offset;	 Catch:{ FileNotFoundException -> 0x01b5, IOException -> 0x01b8, IllegalArgumentException -> 0x01bb, SecurityException -> 0x01be, all -> 0x01b1 }
        r6 = (long) r4;	 Catch:{ FileNotFoundException -> 0x01b5, IOException -> 0x01b8, IllegalArgumentException -> 0x01bb, SecurityException -> 0x01be, all -> 0x01b1 }
        r3.seek(r6);	 Catch:{ FileNotFoundException -> 0x01b5, IOException -> 0x01b8, IllegalArgumentException -> 0x01bb, SecurityException -> 0x01be, all -> 0x01b1 }
        r4 = r5.convertedData;	 Catch:{ FileNotFoundException -> 0x01b5, IOException -> 0x01b8, IllegalArgumentException -> 0x01bb, SecurityException -> 0x01be, all -> 0x01b1 }
        r3.write(r4);	 Catch:{ FileNotFoundException -> 0x01b5, IOException -> 0x01b8, IllegalArgumentException -> 0x01bb, SecurityException -> 0x01be, all -> 0x01b1 }
        if (r3 == 0) goto L_0x0024;
    L_0x003a:
        r3.close();	 Catch:{ IOException -> 0x003f, IllegalStateException -> 0x01cd }
        r2 = r0;
        goto L_0x0025;
    L_0x003f:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r4 = "Failed to close File:";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r4 = ".";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0025;
    L_0x005f:
        r0 = move-exception;
        r3 = r0;
    L_0x0061:
        r0 = "DrmConvertSession";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r4 = "Could not close convertsession. Convertsession: ";
        r1 = r1.append(r4);
        r4 = r8.mConvertSessionId;
        r1 = r1.append(r4);
        r1 = r1.toString();
        android.util.Log.w(r0, r1, r3);
        goto L_0x0025;
    L_0x007c:
        r0 = move-exception;
        r3 = r4;
    L_0x007e:
        r1 = "DrmConvertSession";
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01c4 }
        r4.<init>();	 Catch:{ all -> 0x01c4 }
        r5 = "File: ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c4 }
        r4 = r4.append(r9);	 Catch:{ all -> 0x01c4 }
        r5 = " could not be found.";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c4 }
        r4 = r4.toString();	 Catch:{ all -> 0x01c4 }
        android.util.Log.w(r1, r4, r0);	 Catch:{ all -> 0x01c4 }
        if (r3 == 0) goto L_0x01d2;
    L_0x009e:
        r3.close();	 Catch:{ IOException -> 0x00a2 }
        goto L_0x0025;
    L_0x00a2:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r4 = "Failed to close File:";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r4 = ".";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0025;
    L_0x00c3:
        r0 = move-exception;
        r3 = r4;
    L_0x00c5:
        r1 = "DrmConvertSession";
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01c4 }
        r4.<init>();	 Catch:{ all -> 0x01c4 }
        r5 = "Could not access File: ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c4 }
        r4 = r4.append(r9);	 Catch:{ all -> 0x01c4 }
        r5 = " .";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c4 }
        r4 = r4.toString();	 Catch:{ all -> 0x01c4 }
        android.util.Log.w(r1, r4, r0);	 Catch:{ all -> 0x01c4 }
        if (r3 == 0) goto L_0x01d2;
    L_0x00e5:
        r3.close();	 Catch:{ IOException -> 0x00ea }
        goto L_0x0025;
    L_0x00ea:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r4 = "Failed to close File:";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r4 = ".";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0025;
    L_0x010b:
        r0 = move-exception;
        r3 = r4;
    L_0x010d:
        r1 = "DrmConvertSession";
        r4 = "Could not open file in mode: rw";
        android.util.Log.w(r1, r4, r0);	 Catch:{ all -> 0x01c4 }
        if (r3 == 0) goto L_0x01d2;
    L_0x0116:
        r3.close();	 Catch:{ IOException -> 0x011b }
        goto L_0x0025;
    L_0x011b:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r4 = "Failed to close File:";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r4 = ".";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0025;
    L_0x013c:
        r0 = move-exception;
        r3 = r4;
    L_0x013e:
        r4 = "DrmConvertSession";
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01c1 }
        r5.<init>();	 Catch:{ all -> 0x01c1 }
        r6 = "Access to File: ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x01c1 }
        r5 = r5.append(r9);	 Catch:{ all -> 0x01c1 }
        r6 = " was denied denied by SecurityManager.";
        r5 = r5.append(r6);	 Catch:{ all -> 0x01c1 }
        r5 = r5.toString();	 Catch:{ all -> 0x01c1 }
        android.util.Log.w(r4, r5, r0);	 Catch:{ all -> 0x01c1 }
        if (r3 == 0) goto L_0x01d5;
    L_0x015e:
        r3.close();	 Catch:{ IOException -> 0x0164 }
        r2 = r1;
        goto L_0x0025;
    L_0x0164:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r4 = "Failed to close File:";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r4 = ".";
        r3 = r3.append(r4);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0025;
    L_0x0185:
        r0 = move-exception;
        r5 = r0;
        r3 = r4;
    L_0x0188:
        r0 = r1;
        r4 = r5;
    L_0x018a:
        if (r3 == 0) goto L_0x01d8;
    L_0x018c:
        r3.close();	 Catch:{ IOException -> 0x0191, IllegalStateException -> 0x01cd }
        r2 = r0;
    L_0x0190:
        throw r4;	 Catch:{ IllegalStateException -> 0x005f }
    L_0x0191:
        r0 = move-exception;
        r1 = "DrmConvertSession";
        r3 = new java.lang.StringBuilder;	 Catch:{ IllegalStateException -> 0x005f }
        r3.<init>();	 Catch:{ IllegalStateException -> 0x005f }
        r5 = "Failed to close File:";
        r3 = r3.append(r5);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.append(r9);	 Catch:{ IllegalStateException -> 0x005f }
        r5 = ".";
        r3 = r3.append(r5);	 Catch:{ IllegalStateException -> 0x005f }
        r3 = r3.toString();	 Catch:{ IllegalStateException -> 0x005f }
        android.util.Log.w(r1, r3, r0);	 Catch:{ IllegalStateException -> 0x005f }
        goto L_0x0190;
    L_0x01b1:
        r0 = move-exception;
        r4 = r0;
        r0 = r1;
        goto L_0x018a;
    L_0x01b5:
        r0 = move-exception;
        goto L_0x007e;
    L_0x01b8:
        r0 = move-exception;
        goto L_0x00c5;
    L_0x01bb:
        r0 = move-exception;
        goto L_0x010d;
    L_0x01be:
        r0 = move-exception;
        goto L_0x013e;
    L_0x01c1:
        r0 = move-exception;
        r5 = r0;
        goto L_0x0188;
    L_0x01c4:
        r0 = move-exception;
        r5 = r0;
        r1 = r2;
        goto L_0x0188;
    L_0x01c8:
        r0 = move-exception;
        r3 = r0;
        r2 = r1;
        goto L_0x0061;
    L_0x01cd:
        r1 = move-exception;
        r3 = r1;
        r2 = r0;
        goto L_0x0061;
    L_0x01d2:
        r0 = r2;
        goto L_0x0024;
    L_0x01d5:
        r0 = r1;
        goto L_0x0024;
    L_0x01d8:
        r2 = r0;
        goto L_0x0190;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.util.DrmConvertSession.close(java.lang.String):int");
    }

    public byte[] convert(byte[] bArr, int i) {
        if (bArr != null) {
            try {
                DrmConvertedStatus convertData;
                if (i != bArr.length) {
                    byte[] bArr2 = new byte[i];
                    System.arraycopy(bArr, 0, bArr2, 0, i);
                    convertData = this.mDrmClient.convertData(this.mConvertSessionId, bArr2);
                } else {
                    convertData = this.mDrmClient.convertData(this.mConvertSessionId, bArr);
                }
                return (convertData == null || convertData.statusCode != 1 || convertData.convertedData == null) ? null : convertData.convertedData;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Buffer with data to convert is illegal. Convertsession: " + this.mConvertSessionId, e);
                return null;
            } catch (IllegalStateException e2) {
                Log.w(TAG, "Could not convert data. Convertsession: " + this.mConvertSessionId, e2);
                return null;
            }
        }
        throw new IllegalArgumentException("Parameter inBuffer is null");
    }
}
