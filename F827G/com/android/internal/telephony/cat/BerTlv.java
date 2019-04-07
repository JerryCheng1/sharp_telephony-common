package com.android.internal.telephony.cat;

import java.util.List;

class BerTlv {
    public static final int BER_EVENT_DOWNLOAD_TAG = 214;
    public static final int BER_MENU_SELECTION_TAG = 211;
    public static final int BER_PROACTIVE_COMMAND_TAG = 208;
    public static final int BER_UNKNOWN_TAG = 0;
    private List<ComprehensionTlv> mCompTlvs = null;
    private boolean mLengthValid = true;
    private int mTag = 0;

    private BerTlv(int i, List<ComprehensionTlv> list, boolean z) {
        this.mTag = i;
        this.mCompTlvs = list;
        this.mLengthValid = z;
    }

    /* JADX WARNING: Removed duplicated region for block: B:51:0x0140 A:{ExcHandler: ResultException (e com.android.internal.telephony.cat.ResultException), Splitter:B:13:0x0054} */
    /* JADX WARNING: Exception block dominator not found, dom blocks: [B:13:0x0054, B:25:0x00e8] */
    /* JADX WARNING: Missing block: B:51:0x0140, code skipped:
            r0 = e;
     */
    /* JADX WARNING: Missing block: B:53:0x0143, code skipped:
            r0 = 1;
     */
    public static com.android.internal.telephony.cat.BerTlv decode(byte[] r10) throws com.android.internal.telephony.cat.ResultException {
        /*
        r8 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        r3 = 1;
        r9 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r2 = 0;
        r7 = r10.length;
        r0 = r10[r2];
        r4 = r0 & 255;
        if (r4 != r8) goto L_0x00e8;
    L_0x000d:
        r0 = 2;
        r1 = r10[r3];
        r1 = r1 & 255;
        if (r1 >= r9) goto L_0x0049;
    L_0x0014:
        r5 = r1;
        r6 = r0;
    L_0x0016:
        r0 = r7 - r6;
        if (r0 >= r5) goto L_0x0104;
    L_0x001a:
        r0 = new com.android.internal.telephony.cat.ResultException;
        r1 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "Command had extra data endIndex=";
        r2 = r2.append(r3);
        r2 = r2.append(r7);
        r3 = " curIndex=";
        r2 = r2.append(r3);
        r2 = r2.append(r6);
        r3 = " length=";
        r2 = r2.append(r3);
        r2 = r2.append(r5);
        r2 = r2.toString();
        r0.<init>(r1, r2);
        throw r0;
    L_0x0049:
        r5 = 129; // 0x81 float:1.81E-43 double:6.37E-322;
        if (r1 != r5) goto L_0x00b3;
    L_0x004d:
        r1 = 3;
        r0 = r10[r0];
        r0 = r0 & 255;
        if (r0 >= r9) goto L_0x00af;
    L_0x0054:
        r0 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r2 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r4 = "length < 0x80 length=";
        r3 = r3.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r4 = 0;
        r4 = java.lang.Integer.toHexString(r4);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3 = r3.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r4 = " curIndex=";
        r3 = r3.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3 = r3.append(r1);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r4 = " endIndex=";
        r3 = r3.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3 = r3.append(r7);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r3 = r3.toString();	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        r0.<init>(r2, r3);	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
        throw r0;	 Catch:{ IndexOutOfBoundsException -> 0x0088, ResultException -> 0x0140 }
    L_0x0088:
        r0 = move-exception;
        r0 = r1;
    L_0x008a:
        r1 = new com.android.internal.telephony.cat.ResultException;
        r2 = com.android.internal.telephony.cat.ResultCode.REQUIRED_VALUES_MISSING;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "IndexOutOfBoundsException  curIndex=";
        r3 = r3.append(r4);
        r0 = r3.append(r0);
        r3 = " endIndex=";
        r0 = r0.append(r3);
        r0 = r0.append(r7);
        r0 = r0.toString();
        r1.<init>(r2, r0);
        throw r1;
    L_0x00af:
        r5 = r0;
        r6 = r1;
        goto L_0x0016;
    L_0x00b3:
        r2 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r3 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r4 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r4.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r5 = "Expected first byte to be length or a length tag and < 0x81 byte= ";
        r4 = r4.append(r5);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r1 = java.lang.Integer.toHexString(r1);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r1 = r4.append(r1);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r4 = " curIndex=";
        r1 = r1.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r1 = r1.append(r0);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r4 = " endIndex=";
        r1 = r1.append(r4);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r1 = r1.append(r7);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r1 = r1.toString();	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        r2.<init>(r3, r1);	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
        throw r2;	 Catch:{ IndexOutOfBoundsException -> 0x00e6, ResultException -> 0x00f7 }
    L_0x00e6:
        r1 = move-exception;
        goto L_0x008a;
    L_0x00e8:
        r0 = com.android.internal.telephony.cat.ComprehensionTlvTag.COMMAND_DETAILS;	 Catch:{ IndexOutOfBoundsException -> 0x0142, ResultException -> 0x0140 }
        r0 = r0.value();	 Catch:{ IndexOutOfBoundsException -> 0x0142, ResultException -> 0x0140 }
        r1 = r4 & -129;
        if (r0 != r1) goto L_0x013c;
    L_0x00f2:
        r4 = r2;
        r5 = r2;
        r6 = r2;
        goto L_0x0016;
    L_0x00f7:
        r0 = move-exception;
    L_0x00f8:
        r1 = new com.android.internal.telephony.cat.ResultException;
        r2 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;
        r0 = r0.explanation();
        r1.<init>(r2, r0);
        throw r1;
    L_0x0104:
        r6 = com.android.internal.telephony.cat.ComprehensionTlv.decodeMany(r10, r6);
        if (r4 != r8) goto L_0x014a;
    L_0x010a:
        r7 = r6.iterator();
        r1 = r2;
    L_0x010f:
        r0 = r7.hasNext();
        if (r0 == 0) goto L_0x0148;
    L_0x0115:
        r0 = r7.next();
        r0 = (com.android.internal.telephony.cat.ComprehensionTlv) r0;
        r0 = r0.getLength();
        if (r0 < r9) goto L_0x012a;
    L_0x0121:
        r8 = 255; // 0xff float:3.57E-43 double:1.26E-321;
        if (r0 > r8) goto L_0x012a;
    L_0x0125:
        r0 = r0 + 3;
        r0 = r0 + r1;
        r1 = r0;
        goto L_0x010f;
    L_0x012a:
        if (r0 < 0) goto L_0x0133;
    L_0x012c:
        if (r0 >= r9) goto L_0x0133;
    L_0x012e:
        r0 = r0 + 2;
        r0 = r0 + r1;
        r1 = r0;
        goto L_0x010f;
    L_0x0133:
        r0 = r2;
    L_0x0134:
        if (r5 == r1) goto L_0x0146;
    L_0x0136:
        r0 = new com.android.internal.telephony.cat.BerTlv;
        r0.<init>(r4, r6, r2);
        return r0;
    L_0x013c:
        r5 = r2;
        r6 = r3;
        goto L_0x0016;
    L_0x0140:
        r0 = move-exception;
        goto L_0x00f8;
    L_0x0142:
        r0 = move-exception;
        r0 = r3;
        goto L_0x008a;
    L_0x0146:
        r2 = r0;
        goto L_0x0136;
    L_0x0148:
        r0 = r3;
        goto L_0x0134;
    L_0x014a:
        r2 = r3;
        goto L_0x0136;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.BerTlv.decode(byte[]):com.android.internal.telephony.cat.BerTlv");
    }

    public List<ComprehensionTlv> getComprehensionTlvs() {
        return this.mCompTlvs;
    }

    public int getTag() {
        return this.mTag;
    }

    public boolean isLengthValid() {
        return this.mLengthValid;
    }
}
