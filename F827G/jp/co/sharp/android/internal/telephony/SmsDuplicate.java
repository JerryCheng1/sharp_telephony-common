package jp.co.sharp.android.internal.telephony;

import android.content.Context;
import android.telephony.Rlog;
import com.android.internal.telephony.SMSDispatcher;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedList;

public class SmsDuplicate {
    public static final int INVALIDATE_MESSAGE_ID = -1;
    private static final int MESSAGE_DATA_MAX_SIZE = 200;
    public static final int SMS_DUPLICATE_REPLY_ACK = 0;
    public static final int SMS_DUPLICATE_REPLY_DEPEND_APP = 2;
    public static final int SMS_DUPLICATE_REPLY_NACK = 1;
    public static final int SMS_DUPLICATE_REPLY_NONE = 3;
    private static final String TAG = "SMS";
    protected String DUPLICATE_FILENAME = "duplicate_file.dat";
    private int mCheckedNum;
    private Context mContext;
    private LinkedList<SmsDuplicateInfo> mDuplicateList;
    private SMSDispatcher mSmsDispatcher;
    private boolean mUseFile;

    public class ResultJudgeDuplicate {
        public int mErrorCode = -1;
        public boolean mIsReply = false;
        public boolean mIsSame = false;
        public boolean mResponse = true;

        public ResultJudgeDuplicate(boolean z, boolean z2, int i, boolean z3) {
            this.mIsSame = z;
            this.mResponse = z2;
            this.mErrorCode = i;
            this.mIsReply = z3;
        }
    }

    public class SmsAccessory {
        private String mAction;
        private String mPermission;
        private int mResponse;

        public SmsAccessory(String str, String str2, int i) {
            this.mAction = str;
            this.mPermission = str2;
            this.mResponse = i;
        }

        public String getAction() {
            return this.mAction;
        }

        public String getPermission() {
            return this.mPermission;
        }

        public int getResponse() {
            return this.mResponse;
        }
    }

    protected class SmsDuplicateInfo {
        public int mAck;
        public byte[] mMessageData;
        public int mMessageDataLength;
        public int mMessageID;

        protected SmsDuplicateInfo(int i, int i2, int i3, byte[] bArr) {
            this.mMessageID = i;
            this.mAck = i2;
            this.mMessageDataLength = i3;
            this.mMessageData = bArr;
        }
    }

    public SmsDuplicate(Context context, int i, boolean z) {
        this.mContext = context;
        this.mCheckedNum = i;
        this.mUseFile = z;
    }

    private boolean readDuplicateFile() {
        IOException e;
        boolean z;
        this.mDuplicateList = new LinkedList();
        try {
            DataInputStream dataInputStream = new DataInputStream(this.mContext.openFileInput(this.DUPLICATE_FILENAME));
            while (true) {
                try {
                    int readInt = dataInputStream.readInt();
                    int readInt2 = dataInputStream.readInt();
                    int readInt3 = dataInputStream.readInt();
                    if (readInt3 > 200) {
                        readInt3 = 200;
                    }
                    byte[] bArr = new byte[readInt3];
                    dataInputStream.read(bArr, 0, readInt3);
                    this.mDuplicateList.offer(new SmsDuplicateInfo(readInt, readInt2, readInt3, bArr));
                } catch (EOFException e2) {
                    Rlog.i(TAG, "checkSmsDuplicate() duplicate_file reached EOF ");
                    return true;
                } finally {
                    dataInputStream.close();
                    try {
                    } catch (IOException e3) {
                        e = e3;
                        z = true;
                    }
                }
            }
        } catch (IOException e4) {
            e = e4;
            z = false;
        }
        Rlog.e(TAG, "checkSmsDuplicate() failed to read duplicate_file ");
        e.printStackTrace();
        return z;
    }

    public SmsAccessory SmsAccessory(String str, String str2, int i) {
        return new SmsAccessory(str, str2, 0);
    }

    public ResultJudgeDuplicate checkSmsDuplicate(int i, byte[] bArr) {
        ResultJudgeDuplicate resultJudgeDuplicate = new ResultJudgeDuplicate(false, true, -1, true);
        if (this.mDuplicateList == null && this.mUseFile && !readDuplicateFile()) {
            Rlog.e(TAG, "readDuplicateFile() failed to read duplicate_file ");
            return resultJudgeDuplicate;
        } else if (this.mDuplicateList == null) {
            return resultJudgeDuplicate;
        } else {
            boolean z = false;
            int i2 = 0;
            while (i2 < this.mDuplicateList.size()) {
                SmsDuplicateInfo smsDuplicateInfo = (SmsDuplicateInfo) this.mDuplicateList.get(i2);
                if (smsDuplicateInfo.mMessageID == i) {
                    byte[] bArr2 = smsDuplicateInfo.mMessageData;
                    if (smsDuplicateInfo.mMessageDataLength == bArr.length) {
                        int i3 = 0;
                        while (i3 < smsDuplicateInfo.mMessageDataLength) {
                            if (bArr2[i3] != bArr[i3]) {
                                Rlog.d(TAG, "sameData is false, notificate this sms.");
                                z = false;
                                break;
                            }
                            i3++;
                            z = true;
                        }
                    }
                }
                if (z) {
                    Rlog.d(TAG, "sameData is true, discard this sms.");
                    break;
                }
                i2++;
            }
            boolean z2 = z;
            return z2 ? ((SmsDuplicateInfo) this.mDuplicateList.get(i2)).mAck == 0 ? new ResultJudgeDuplicate(z2, true, -1, true) : ((SmsDuplicateInfo) this.mDuplicateList.get(i2)).mAck == 1 ? new ResultJudgeDuplicate(z2, false, 3, true) : new ResultJudgeDuplicate(z2, true, -1, false) : resultJudgeDuplicate;
        }
    }

    public void updateSmsDuplicate(int i, byte[] bArr, SmsAccessory smsAccessory) {
        if (this.mDuplicateList == null) {
            this.mDuplicateList = new LinkedList();
        }
        SmsDuplicateInfo smsDuplicateInfo = new SmsDuplicateInfo(i, smsAccessory.getResponse(), bArr.length, bArr);
        if (this.mDuplicateList.size() >= this.mCheckedNum) {
            int size = this.mDuplicateList.size();
            int i2 = this.mCheckedNum;
            for (int i3 = 0; i3 < (size - i2) + 1; i3++) {
                this.mDuplicateList.poll();
            }
        }
        this.mDuplicateList.offer(smsDuplicateInfo);
        if (smsDuplicateInfo.mAck != 2 && this.mUseFile) {
            updateSmsDuplicateFile(smsDuplicateInfo.mAck, smsDuplicateInfo.mMessageID);
        }
    }

    public void updateSmsDuplicateFile(int i, int i2) {
        try {
            if (((SmsDuplicateInfo) this.mDuplicateList.getLast()).mMessageID == i2) {
                if (((SmsDuplicateInfo) this.mDuplicateList.getLast()).mAck == 2) {
                    ((SmsDuplicateInfo) this.mDuplicateList.getLast()).mAck = i;
                }
                DataOutputStream dataOutputStream = new DataOutputStream(this.mContext.openFileOutput(this.DUPLICATE_FILENAME, 0));
                for (int i3 = 0; i3 < this.mDuplicateList.size(); i3++) {
                    dataOutputStream.writeInt(((SmsDuplicateInfo) this.mDuplicateList.get(i3)).mMessageID);
                    dataOutputStream.writeInt(((SmsDuplicateInfo) this.mDuplicateList.get(i3)).mAck);
                    dataOutputStream.writeInt(((SmsDuplicateInfo) this.mDuplicateList.get(i3)).mMessageDataLength);
                    dataOutputStream.write(((SmsDuplicateInfo) this.mDuplicateList.get(i3)).mMessageData);
                }
                dataOutputStream.flush();
                dataOutputStream.close();
                return;
            }
            Rlog.e(TAG, "updateSmsDuplicateFile() MessageID is different ");
        } catch (IOException e) {
            Rlog.e(TAG, "updateSmsDuplicateFile() failed to update duplicate_file ");
            e.printStackTrace();
        } catch (NullPointerException e2) {
            Rlog.e(TAG, "updateSmsDuplicateFile() the error by NullPointerException ");
        }
    }
}
