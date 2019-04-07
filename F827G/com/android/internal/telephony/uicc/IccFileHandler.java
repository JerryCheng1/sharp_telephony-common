package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.CommandsInterface;
import java.util.ArrayList;

public abstract class IccFileHandler extends Handler implements IccConstants {
    protected static final int COMMAND_GET_RESPONSE = 192;
    protected static final int COMMAND_READ_BINARY = 176;
    protected static final int COMMAND_READ_RECORD = 178;
    protected static final int COMMAND_SEEK = 162;
    protected static final int COMMAND_UPDATE_BINARY = 214;
    protected static final int COMMAND_UPDATE_RECORD = 220;
    protected static final int EF_TYPE_CYCLIC = 3;
    protected static final int EF_TYPE_LINEAR_FIXED = 1;
    protected static final int EF_TYPE_TRANSPARENT = 0;
    protected static final int EVENT_GET_BINARY_SIZE_DONE = 4;
    protected static final int EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE = 8;
    protected static final int EVENT_GET_RECORD_SIZE_DONE = 6;
    protected static final int EVENT_GET_RECORD_SIZE_IMG_DONE = 11;
    protected static final int EVENT_READ_BINARY_DONE = 5;
    protected static final int EVENT_READ_ICON_DONE = 10;
    protected static final int EVENT_READ_IMG_DONE = 9;
    protected static final int EVENT_READ_RECORD_DONE = 7;
    protected static final int GET_RESPONSE_EF_IMG_SIZE_BYTES = 10;
    protected static final int GET_RESPONSE_EF_SIZE_BYTES = 15;
    protected static final int READ_RECORD_MODE_ABSOLUTE = 4;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_1 = 8;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_2 = 9;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_3 = 10;
    protected static final int RESPONSE_DATA_FILE_ID_1 = 4;
    protected static final int RESPONSE_DATA_FILE_ID_2 = 5;
    protected static final int RESPONSE_DATA_FILE_SIZE_1 = 2;
    protected static final int RESPONSE_DATA_FILE_SIZE_2 = 3;
    protected static final int RESPONSE_DATA_FILE_STATUS = 11;
    protected static final int RESPONSE_DATA_FILE_TYPE = 6;
    protected static final int RESPONSE_DATA_LENGTH = 12;
    protected static final int RESPONSE_DATA_RECORD_LENGTH = 14;
    protected static final int RESPONSE_DATA_RFU_1 = 0;
    protected static final int RESPONSE_DATA_RFU_2 = 1;
    protected static final int RESPONSE_DATA_RFU_3 = 7;
    protected static final int RESPONSE_DATA_STRUCTURE = 13;
    protected static final int TYPE_DF = 2;
    protected static final int TYPE_EF = 4;
    protected static final int TYPE_MF = 1;
    protected static final int TYPE_RFU = 0;
    protected final String mAid;
    protected final CommandsInterface mCi;
    protected final UiccCardApplication mParentApp;

    static class LoadLinearFixedContext {
        int mCount;
        int mCountLoadrecords;
        int mCountRecords;
        int mEfid;
        boolean mLoadAll;
        boolean mLoadPart;
        Message mOnLoaded;
        String mPath;
        int mRecordNum;
        ArrayList<Integer> mRecordNums;
        int mRecordSize;
        ArrayList<byte[]> results;

        LoadLinearFixedContext(int i, int i2, Message message) {
            this.mEfid = i;
            this.mRecordNum = i2;
            this.mOnLoaded = message;
            this.mLoadAll = false;
            this.mLoadPart = false;
            this.mPath = null;
        }

        LoadLinearFixedContext(int i, int i2, String str, Message message) {
            this.mEfid = i;
            this.mRecordNum = i2;
            this.mOnLoaded = message;
            this.mLoadAll = false;
            this.mLoadPart = false;
            this.mPath = str;
        }

        LoadLinearFixedContext(int i, Message message) {
            this.mEfid = i;
            this.mRecordNum = 1;
            this.mLoadAll = true;
            this.mLoadPart = false;
            this.mOnLoaded = message;
            this.mPath = null;
        }

        LoadLinearFixedContext(int i, String str, Message message) {
            this.mEfid = i;
            this.mRecordNum = 1;
            this.mLoadAll = true;
            this.mLoadPart = false;
            this.mOnLoaded = message;
            this.mPath = str;
        }

        LoadLinearFixedContext(int i, ArrayList<Integer> arrayList, String str, Message message) {
            this.mEfid = i;
            this.mRecordNum = ((Integer) arrayList.get(0)).intValue();
            this.mLoadAll = false;
            this.mLoadPart = true;
            this.mRecordNums = new ArrayList();
            this.mRecordNums.addAll(arrayList);
            this.mCount = 0;
            this.mCountLoadrecords = arrayList.size();
            this.mOnLoaded = message;
            this.mPath = str;
        }

        private void initLCResults(int i) {
            int i2 = 0;
            this.results = new ArrayList(i);
            byte[] bArr = new byte[this.mRecordSize];
            for (int i3 = 0; i3 < this.mRecordSize; i3++) {
                bArr[i3] = (byte) -1;
            }
            while (i2 < i) {
                this.results.add(bArr);
                i2++;
            }
        }
    }

    protected IccFileHandler(UiccCardApplication uiccCardApplication, String str, CommandsInterface commandsInterface) {
        this.mParentApp = uiccCardApplication;
        this.mAid = str;
        this.mCi = commandsInterface;
    }

    private boolean processException(Message message, AsyncResult asyncResult) {
        boolean z;
        IccIoResult iccIoResult = (IccIoResult) asyncResult.result;
        if (asyncResult.exception != null) {
            sendResult(message, null, asyncResult.exception);
            z = true;
        } else {
            IccException exception = iccIoResult.getException();
            if (exception != null) {
                sendResult(message, null, exception);
                return true;
            }
            z = false;
        }
        return z;
    }

    private void sendResult(Message message, Object obj, Throwable th) {
        if (message != null) {
            AsyncResult.forMessage(message, obj, th);
            message.sendToTarget();
        }
    }

    public void dispose() {
    }

    /* Access modifiers changed, original: protected */
    public String getCommonIccEFPath(int i) {
        switch (i) {
            case IccConstants.EF_PL /*12037*/:
            case IccConstants.EF_ICCID /*12258*/:
                return IccConstants.MF_SIM;
            case 20256:
                return "3F007F105F50";
            case IccConstants.EF_PBR /*20272*/:
                return "3F007F105F3A";
            case 28474:
            case IccConstants.EF_FDN /*28475*/:
            case IccConstants.EF_MSISDN /*28480*/:
            case IccConstants.EF_SDN /*28489*/:
            case IccConstants.EF_EXT1 /*28490*/:
            case IccConstants.EF_EXT2 /*28491*/:
            case IccConstants.EF_EXT3 /*28492*/:
            case IccConstants.EF_PSI /*28645*/:
                return "3F007F10";
            default:
                return null;
        }
    }

    public void getEFLinearRecordSize(int i, Message message) {
        getEFLinearRecordSize(i, getEFPath(i), message);
    }

    public void getEFLinearRecordSize(int i, String str, Message message) {
        int i2 = i;
        String str2 = str;
        int i3 = 0;
        String str3 = null;
        this.mCi.iccIOForApp(192, i2, str2, 0, i3, 15, null, str3, this.mAid, obtainMessage(8, new LoadLinearFixedContext(i, str, message)));
    }

    public abstract String getEFPath(int i);

    public void handleMessage(android.os.Message r16) {
        /*
        r15 = this;
        r7 = 2;
        r6 = 1;
        r4 = 0;
        r5 = 4;
        r13 = 0;
        r0 = r16;
        r1 = r0.what;	 Catch:{ Exception -> 0x025c }
        switch(r1) {
            case 4: goto L_0x00f6;
            case 5: goto L_0x021e;
            case 6: goto L_0x006b;
            case 7: goto L_0x0154;
            case 8: goto L_0x000d;
            case 9: goto L_0x0154;
            case 10: goto L_0x021e;
            case 11: goto L_0x006b;
            default: goto L_0x000c;
        };	 Catch:{ Exception -> 0x025c }
    L_0x000c:
        return;
    L_0x000d:
        r0 = r16;
        r1 = r0.obj;	 Catch:{ Exception -> 0x025c }
        r1 = (android.os.AsyncResult) r1;	 Catch:{ Exception -> 0x025c }
        r2 = r1.userObj;	 Catch:{ Exception -> 0x025c }
        r2 = (com.android.internal.telephony.uicc.IccFileHandler.LoadLinearFixedContext) r2;	 Catch:{ Exception -> 0x025c }
        r1 = r1.result;	 Catch:{ Exception -> 0x025c }
        r1 = (com.android.internal.telephony.uicc.IccIoResult) r1;	 Catch:{ Exception -> 0x025c }
        r12 = r2.mOnLoaded;	 Catch:{ Exception -> 0x025c }
        r0 = r16;
        r2 = r0.obj;	 Catch:{ Exception -> 0x003c }
        r2 = (android.os.AsyncResult) r2;	 Catch:{ Exception -> 0x003c }
        r2 = r15.processException(r12, r2);	 Catch:{ Exception -> 0x003c }
        if (r2 != 0) goto L_0x000c;
    L_0x0029:
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r2 = 6;
        r2 = r1[r2];
        if (r5 != r2) goto L_0x0036;
    L_0x0030:
        r2 = 13;
        r2 = r1[r2];
        if (r6 == r2) goto L_0x0043;
    L_0x0036:
        r1 = new com.android.internal.telephony.uicc.IccFileTypeMismatch;	 Catch:{ Exception -> 0x003c }
        r1.<init>();	 Catch:{ Exception -> 0x003c }
        throw r1;	 Catch:{ Exception -> 0x003c }
    L_0x003c:
        r1 = move-exception;
    L_0x003d:
        if (r12 == 0) goto L_0x0244;
    L_0x003f:
        r15.sendResult(r12, r13, r1);
        goto L_0x000c;
    L_0x0043:
        r2 = 3;
        r2 = new int[r2];	 Catch:{ Exception -> 0x003c }
        r3 = 14;
        r3 = r1[r3];
        r3 = r3 & 255;
        r2[r4] = r3;
        r3 = r1[r7];
        r3 = r3 & 255;
        r3 = r3 << 8;
        r4 = 3;
        r1 = r1[r4];
        r1 = r1 & 255;
        r1 = r1 + r3;
        r2[r6] = r1;
        r1 = 2;
        r3 = 1;
        r3 = r2[r3];	 Catch:{ Exception -> 0x003c }
        r4 = 0;
        r4 = r2[r4];	 Catch:{ Exception -> 0x003c }
        r3 = r3 / r4;
        r2[r1] = r3;	 Catch:{ Exception -> 0x003c }
        r1 = 0;
        r15.sendResult(r12, r2, r1);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x006b:
        r0 = r16;
        r1 = r0.obj;	 Catch:{ Exception -> 0x025c }
        r1 = (android.os.AsyncResult) r1;	 Catch:{ Exception -> 0x025c }
        r2 = r1.userObj;	 Catch:{ Exception -> 0x025c }
        r0 = r2;
        r0 = (com.android.internal.telephony.uicc.IccFileHandler.LoadLinearFixedContext) r0;	 Catch:{ Exception -> 0x025c }
        r11 = r0;
        r1 = r1.result;	 Catch:{ Exception -> 0x025c }
        r1 = (com.android.internal.telephony.uicc.IccIoResult) r1;	 Catch:{ Exception -> 0x025c }
        r12 = r11.mOnLoaded;	 Catch:{ Exception -> 0x025c }
        r0 = r16;
        r2 = r0.obj;	 Catch:{ Exception -> 0x003c }
        r2 = (android.os.AsyncResult) r2;	 Catch:{ Exception -> 0x003c }
        r2 = r15.processException(r12, r2);	 Catch:{ Exception -> 0x003c }
        if (r2 != 0) goto L_0x000c;
    L_0x0089:
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r4 = r11.mPath;	 Catch:{ Exception -> 0x003c }
        r2 = 6;
        r2 = r1[r2];
        if (r5 == r2) goto L_0x0098;
    L_0x0092:
        r1 = new com.android.internal.telephony.uicc.IccFileTypeMismatch;	 Catch:{ Exception -> 0x003c }
        r1.<init>();	 Catch:{ Exception -> 0x003c }
        throw r1;	 Catch:{ Exception -> 0x003c }
    L_0x0098:
        r2 = 13;
        r2 = r1[r2];
        if (r6 == r2) goto L_0x00a4;
    L_0x009e:
        r1 = new com.android.internal.telephony.uicc.IccFileTypeMismatch;	 Catch:{ Exception -> 0x003c }
        r1.<init>();	 Catch:{ Exception -> 0x003c }
        throw r1;	 Catch:{ Exception -> 0x003c }
    L_0x00a4:
        r2 = 14;
        r2 = r1[r2];	 Catch:{ Exception -> 0x003c }
        r2 = r2 & 255;
        r11.mRecordSize = r2;	 Catch:{ Exception -> 0x003c }
        r2 = 2;
        r2 = r1[r2];	 Catch:{ Exception -> 0x003c }
        r2 = r2 & 255;
        r2 = r2 << 8;
        r3 = 3;
        r1 = r1[r3];	 Catch:{ Exception -> 0x003c }
        r1 = r1 & 255;
        r1 = r1 + r2;
        r2 = r11.mRecordSize;	 Catch:{ Exception -> 0x003c }
        r1 = r1 / r2;
        r11.mCountRecords = r1;	 Catch:{ Exception -> 0x003c }
        r1 = r11.mLoadAll;	 Catch:{ Exception -> 0x003c }
        if (r1 == 0) goto L_0x00ec;
    L_0x00c2:
        r1 = new java.util.ArrayList;	 Catch:{ Exception -> 0x003c }
        r2 = r11.mCountRecords;	 Catch:{ Exception -> 0x003c }
        r1.<init>(r2);	 Catch:{ Exception -> 0x003c }
        r11.results = r1;	 Catch:{ Exception -> 0x003c }
    L_0x00cb:
        if (r4 != 0) goto L_0x00d3;
    L_0x00cd:
        r1 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r4 = r15.getEFPath(r1);	 Catch:{ Exception -> 0x003c }
    L_0x00d3:
        r1 = r15.mCi;	 Catch:{ Exception -> 0x003c }
        r2 = 178; // 0xb2 float:2.5E-43 double:8.8E-322;
        r3 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r5 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r6 = 4;
        r7 = r11.mRecordSize;	 Catch:{ Exception -> 0x003c }
        r8 = 0;
        r9 = 0;
        r10 = r15.mAid;	 Catch:{ Exception -> 0x003c }
        r14 = 7;
        r11 = r15.obtainMessage(r14, r11);	 Catch:{ Exception -> 0x003c }
        r1.iccIOForApp(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x00ec:
        r1 = r11.mLoadPart;	 Catch:{ Exception -> 0x003c }
        if (r1 == 0) goto L_0x00cb;
    L_0x00f0:
        r1 = r11.mCountRecords;	 Catch:{ Exception -> 0x003c }
        r11.initLCResults(r1);	 Catch:{ Exception -> 0x003c }
        goto L_0x00cb;
    L_0x00f6:
        r0 = r16;
        r1 = r0.obj;	 Catch:{ Exception -> 0x025c }
        r1 = (android.os.AsyncResult) r1;	 Catch:{ Exception -> 0x025c }
        r2 = r1.userObj;	 Catch:{ Exception -> 0x025c }
        r0 = r2;
        r0 = (android.os.Message) r0;	 Catch:{ Exception -> 0x025c }
        r12 = r0;
        r1 = r1.result;	 Catch:{ Exception -> 0x003c }
        r1 = (com.android.internal.telephony.uicc.IccIoResult) r1;	 Catch:{ Exception -> 0x003c }
        r0 = r16;
        r2 = r0.obj;	 Catch:{ Exception -> 0x003c }
        r2 = (android.os.AsyncResult) r2;	 Catch:{ Exception -> 0x003c }
        r2 = r15.processException(r12, r2);	 Catch:{ Exception -> 0x003c }
        if (r2 != 0) goto L_0x000c;
    L_0x0112:
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r0 = r16;
        r3 = r0.arg1;	 Catch:{ Exception -> 0x003c }
        r2 = 6;
        r2 = r1[r2];
        if (r5 == r2) goto L_0x0123;
    L_0x011d:
        r1 = new com.android.internal.telephony.uicc.IccFileTypeMismatch;	 Catch:{ Exception -> 0x003c }
        r1.<init>();	 Catch:{ Exception -> 0x003c }
        throw r1;	 Catch:{ Exception -> 0x003c }
    L_0x0123:
        r2 = 13;
        r2 = r1[r2];
        if (r2 == 0) goto L_0x012f;
    L_0x0129:
        r1 = new com.android.internal.telephony.uicc.IccFileTypeMismatch;	 Catch:{ Exception -> 0x003c }
        r1.<init>();	 Catch:{ Exception -> 0x003c }
        throw r1;	 Catch:{ Exception -> 0x003c }
    L_0x012f:
        r7 = r1[r7];
        r2 = 3;
        r8 = r1[r2];
        r1 = r15.mCi;	 Catch:{ Exception -> 0x003c }
        r2 = 176; // 0xb0 float:2.47E-43 double:8.7E-322;
        r4 = r15.getEFPath(r3);	 Catch:{ Exception -> 0x003c }
        r5 = 0;
        r6 = 0;
        r7 = r7 & 255;
        r7 = r7 << 8;
        r8 = r8 & 255;
        r7 = r7 + r8;
        r8 = 0;
        r9 = 0;
        r10 = r15.mAid;	 Catch:{ Exception -> 0x003c }
        r11 = 5;
        r14 = 0;
        r11 = r15.obtainMessage(r11, r3, r14, r12);	 Catch:{ Exception -> 0x003c }
        r1.iccIOForApp(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x0154:
        r0 = r16;
        r1 = r0.obj;	 Catch:{ Exception -> 0x025c }
        r1 = (android.os.AsyncResult) r1;	 Catch:{ Exception -> 0x025c }
        r2 = r1.userObj;	 Catch:{ Exception -> 0x025c }
        r0 = r2;
        r0 = (com.android.internal.telephony.uicc.IccFileHandler.LoadLinearFixedContext) r0;	 Catch:{ Exception -> 0x025c }
        r11 = r0;
        r1 = r1.result;	 Catch:{ Exception -> 0x025c }
        r1 = (com.android.internal.telephony.uicc.IccIoResult) r1;	 Catch:{ Exception -> 0x025c }
        r12 = r11.mOnLoaded;	 Catch:{ Exception -> 0x025c }
        r4 = r11.mPath;	 Catch:{ Exception -> 0x003c }
        r0 = r16;
        r2 = r0.obj;	 Catch:{ Exception -> 0x003c }
        r2 = (android.os.AsyncResult) r2;	 Catch:{ Exception -> 0x003c }
        r2 = r15.processException(r12, r2);	 Catch:{ Exception -> 0x003c }
        if (r2 != 0) goto L_0x000c;
    L_0x0174:
        r2 = r11.mLoadAll;	 Catch:{ Exception -> 0x003c }
        if (r2 == 0) goto L_0x01b4;
    L_0x0178:
        r2 = r11.results;	 Catch:{ Exception -> 0x003c }
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r2.add(r1);	 Catch:{ Exception -> 0x003c }
        r1 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r1 = r1 + 1;
        r11.mRecordNum = r1;	 Catch:{ Exception -> 0x003c }
        r1 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r2 = r11.mCountRecords;	 Catch:{ Exception -> 0x003c }
        if (r1 <= r2) goto L_0x0193;
    L_0x018b:
        r1 = r11.results;	 Catch:{ Exception -> 0x003c }
        r2 = 0;
        r15.sendResult(r12, r1, r2);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x0193:
        if (r4 != 0) goto L_0x019b;
    L_0x0195:
        r1 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r4 = r15.getEFPath(r1);	 Catch:{ Exception -> 0x003c }
    L_0x019b:
        r1 = r15.mCi;	 Catch:{ Exception -> 0x003c }
        r2 = 178; // 0xb2 float:2.5E-43 double:8.8E-322;
        r3 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r5 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r6 = 4;
        r7 = r11.mRecordSize;	 Catch:{ Exception -> 0x003c }
        r8 = 0;
        r9 = 0;
        r10 = r15.mAid;	 Catch:{ Exception -> 0x003c }
        r14 = 7;
        r11 = r15.obtainMessage(r14, r11);	 Catch:{ Exception -> 0x003c }
        r1.iccIOForApp(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x01b4:
        r2 = r11.mLoadPart;	 Catch:{ Exception -> 0x003c }
        if (r2 == 0) goto L_0x0216;
    L_0x01b8:
        r2 = r11.results;	 Catch:{ Exception -> 0x003c }
        r3 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r3 = r3 + -1;
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r2.set(r3, r1);	 Catch:{ Exception -> 0x003c }
        r1 = r11.mCount;	 Catch:{ Exception -> 0x003c }
        r1 = r1 + 1;
        r11.mCount = r1;	 Catch:{ Exception -> 0x003c }
        r1 = r11.mCount;	 Catch:{ Exception -> 0x003c }
        r2 = r11.mCountLoadrecords;	 Catch:{ Exception -> 0x003c }
        if (r1 >= r2) goto L_0x020e;
    L_0x01cf:
        r1 = r11.mRecordNums;	 Catch:{ Exception -> 0x003c }
        r2 = r11.mCount;	 Catch:{ Exception -> 0x003c }
        r1 = r1.get(r2);	 Catch:{ Exception -> 0x003c }
        r1 = (java.lang.Integer) r1;	 Catch:{ Exception -> 0x003c }
        r1 = r1.intValue();	 Catch:{ Exception -> 0x003c }
        r11.mRecordNum = r1;	 Catch:{ Exception -> 0x003c }
        r1 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r2 = r11.mCountRecords;	 Catch:{ Exception -> 0x003c }
        if (r1 > r2) goto L_0x0206;
    L_0x01e5:
        if (r4 != 0) goto L_0x01ed;
    L_0x01e7:
        r1 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r4 = r15.getEFPath(r1);	 Catch:{ Exception -> 0x003c }
    L_0x01ed:
        r1 = r15.mCi;	 Catch:{ Exception -> 0x003c }
        r2 = 178; // 0xb2 float:2.5E-43 double:8.8E-322;
        r3 = r11.mEfid;	 Catch:{ Exception -> 0x003c }
        r5 = r11.mRecordNum;	 Catch:{ Exception -> 0x003c }
        r6 = 4;
        r7 = r11.mRecordSize;	 Catch:{ Exception -> 0x003c }
        r8 = 0;
        r9 = 0;
        r10 = r15.mAid;	 Catch:{ Exception -> 0x003c }
        r14 = 7;
        r11 = r15.obtainMessage(r14, r11);	 Catch:{ Exception -> 0x003c }
        r1.iccIOForApp(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x0206:
        r1 = r11.results;	 Catch:{ Exception -> 0x003c }
        r2 = 0;
        r15.sendResult(r12, r1, r2);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x020e:
        r1 = r11.results;	 Catch:{ Exception -> 0x003c }
        r2 = 0;
        r15.sendResult(r12, r1, r2);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x0216:
        r1 = r1.payload;	 Catch:{ Exception -> 0x003c }
        r2 = 0;
        r15.sendResult(r12, r1, r2);	 Catch:{ Exception -> 0x003c }
        goto L_0x000c;
    L_0x021e:
        r0 = r16;
        r1 = r0.obj;	 Catch:{ Exception -> 0x025c }
        r1 = (android.os.AsyncResult) r1;	 Catch:{ Exception -> 0x025c }
        r2 = r1.userObj;	 Catch:{ Exception -> 0x025c }
        r2 = (android.os.Message) r2;	 Catch:{ Exception -> 0x025c }
        r1 = r1.result;	 Catch:{ Exception -> 0x0240 }
        r1 = (com.android.internal.telephony.uicc.IccIoResult) r1;	 Catch:{ Exception -> 0x0240 }
        r0 = r16;
        r3 = r0.obj;	 Catch:{ Exception -> 0x0240 }
        r3 = (android.os.AsyncResult) r3;	 Catch:{ Exception -> 0x0240 }
        r3 = r15.processException(r2, r3);	 Catch:{ Exception -> 0x0240 }
        if (r3 != 0) goto L_0x000c;
    L_0x0238:
        r1 = r1.payload;	 Catch:{ Exception -> 0x0240 }
        r3 = 0;
        r15.sendResult(r2, r1, r3);	 Catch:{ Exception -> 0x0240 }
        goto L_0x000c;
    L_0x0240:
        r1 = move-exception;
        r12 = r2;
        goto L_0x003d;
    L_0x0244:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "uncaught exception";
        r2 = r2.append(r3);
        r1 = r2.append(r1);
        r1 = r1.toString();
        r15.loge(r1);
        goto L_0x000c;
    L_0x025c:
        r1 = move-exception;
        r12 = r13;
        goto L_0x003d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccFileHandler.handleMessage(android.os.Message):void");
    }

    public void loadEFImgLinearFixed(int i, Message message) {
        int i2 = i;
        String str = null;
        this.mCi.iccIOForApp(192, 20256, getEFPath(20256), i2, 4, 10, null, str, this.mAid, obtainMessage(11, new LoadLinearFixedContext(20256, i, message)));
    }

    public void loadEFImgTransparent(int i, int i2, int i3, int i4, Message message) {
        Message obtainMessage = obtainMessage(10, i, 0, message);
        logd("IccFileHandler: loadEFImgTransparent fileid = " + i + " filePath = " + getEFPath(20256) + " highOffset = " + i2 + " lowOffset = " + i3 + " length = " + i4);
        this.mCi.iccIOForApp(176, i, getEFPath(20256), i2, i3, i4, null, null, this.mAid, obtainMessage);
    }

    public void loadEFLinearFixed(int i, int i2, Message message) {
        loadEFLinearFixed(i, getEFPath(i), i2, message);
    }

    public void loadEFLinearFixed(int i, String str, int i2, Message message) {
        int i3 = i;
        String str2 = str;
        int i4 = 0;
        String str3 = null;
        this.mCi.iccIOForApp(192, i3, str2, 0, i4, 15, null, str3, this.mAid, obtainMessage(6, new LoadLinearFixedContext(i, i2, str, message)));
    }

    public void loadEFLinearFixedAll(int i, Message message) {
        loadEFLinearFixedAll(i, getEFPath(i), message);
    }

    public void loadEFLinearFixedAll(int i, String str, Message message) {
        int i2 = i;
        String str2 = str;
        int i3 = 0;
        String str3 = null;
        this.mCi.iccIOForApp(192, i2, str2, 0, i3, 15, null, str3, this.mAid, obtainMessage(6, new LoadLinearFixedContext(i, str, message)));
    }

    public void loadEFLinearFixedPart(int i, String str, ArrayList<Integer> arrayList, Message message) {
        int i2 = i;
        String str2 = str;
        int i3 = 0;
        String str3 = null;
        this.mCi.iccIOForApp(192, i2, str2, 0, i3, 15, null, str3, this.mAid, obtainMessage(6, new LoadLinearFixedContext(i, (ArrayList) arrayList, str, message)));
    }

    public void loadEFLinearFixedPart(int i, ArrayList<Integer> arrayList, Message message) {
        loadEFLinearFixedPart(i, getEFPath(i), arrayList, message);
    }

    public void loadEFTransparent(int i, int i2, Message message) {
        int i3 = i;
        int i4 = 0;
        int i5 = i2;
        String str = null;
        this.mCi.iccIOForApp(176, i3, getEFPath(i), 0, i4, i5, null, str, this.mAid, obtainMessage(5, i, 0, message));
    }

    public void loadEFTransparent(int i, Message message) {
        int i2 = i;
        int i3 = 0;
        String str = null;
        this.mCi.iccIOForApp(192, i2, getEFPath(i), 0, i3, 15, null, str, this.mAid, obtainMessage(4, i, 0, message));
    }

    public abstract void logd(String str);

    public abstract void loge(String str);

    public void updateEFLinearFixed(int i, int i2, byte[] bArr, String str, Message message) {
        this.mCi.iccIOForApp(COMMAND_UPDATE_RECORD, i, getEFPath(i), i2, 4, bArr.length, IccUtils.bytesToHexString(bArr), str, this.mAid, message);
    }

    public void updateEFLinearFixed(int i, String str, int i2, byte[] bArr, String str2, Message message) {
        this.mCi.iccIOForApp(COMMAND_UPDATE_RECORD, i, str, i2, 4, bArr.length, IccUtils.bytesToHexString(bArr), str2, this.mAid, message);
    }

    public void updateEFTransparent(int i, byte[] bArr, Message message) {
        this.mCi.iccIOForApp(214, i, getEFPath(i), 0, 0, bArr.length, IccUtils.bytesToHexString(bArr), null, this.mAid, message);
    }
}
