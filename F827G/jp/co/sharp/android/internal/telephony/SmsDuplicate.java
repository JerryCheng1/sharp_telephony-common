package jp.co.sharp.android.internal.telephony;

import android.content.Context;
import android.telephony.Rlog;
import com.android.internal.telephony.SMSDispatcher;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedList;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    public SmsDuplicate(Context context, int checkedNum, boolean useFile) {
        this.mContext = context;
        this.mCheckedNum = checkedNum;
        this.mUseFile = useFile;
    }

    public ResultJudgeDuplicate checkSmsDuplicate(int messageId, byte[] messageData) {
        boolean sameData = false;
        int listNum = 0;
        ResultJudgeDuplicate info = new ResultJudgeDuplicate(false, true, -1, true);
        if (this.mDuplicateList == null && this.mUseFile && !readDuplicateFile()) {
            Rlog.e(TAG, "readDuplicateFile() failed to read duplicate_file ");
            return info;
        }
        if (this.mDuplicateList != null) {
            while (true) {
                if (listNum >= this.mDuplicateList.size()) {
                    break;
                }
                SmsDuplicateInfo duplicateInfo = this.mDuplicateList.get(listNum);
                if (duplicateInfo.mMessageID == messageId) {
                    byte[] message = duplicateInfo.mMessageData;
                    if (duplicateInfo.mMessageDataLength == messageData.length) {
                        int index = 0;
                        while (true) {
                            if (index < duplicateInfo.mMessageDataLength) {
                                if (message[index] != messageData[index]) {
                                    sameData = false;
                                    Rlog.d(TAG, "sameData is false, notificate this sms.");
                                    break;
                                }
                                sameData = true;
                                index++;
                            } else {
                                break;
                            }
                        }
                    }
                }
                if (sameData) {
                    Rlog.d(TAG, "sameData is true, discard this sms.");
                    break;
                }
                listNum++;
            }
            if (sameData) {
                info = this.mDuplicateList.get(listNum).mAck == 0 ? new ResultJudgeDuplicate(sameData, true, -1, true) : this.mDuplicateList.get(listNum).mAck == 1 ? new ResultJudgeDuplicate(sameData, false, 3, true) : new ResultJudgeDuplicate(sameData, true, -1, false);
            }
        }
        return info;
    }

    private boolean readDuplicateFile() {
        boolean result = false;
        this.mDuplicateList = new LinkedList<>();
        try {
            DataInputStream din = new DataInputStream(this.mContext.openFileInput(this.DUPLICATE_FILENAME));
            while (true) {
                try {
                    int messageId = din.readInt();
                    int sendAck = din.readInt();
                    int length = din.readInt();
                    if (length > 200) {
                        length = 200;
                    }
                    byte[] data = new byte[length];
                    din.read(data, 0, length);
                    this.mDuplicateList.offer(new SmsDuplicateInfo(messageId, sendAck, length, data));
                } catch (EOFException e) {
                    Rlog.i(TAG, "checkSmsDuplicate() duplicate_file reached EOF ");
                    din.close();
                    result = true;
                    return true;
                }
            }
        } catch (IOException e2) {
            Rlog.e(TAG, "checkSmsDuplicate() failed to read duplicate_file ");
            e2.printStackTrace();
            return result;
        }
    }

    public void updateSmsDuplicate(int messageID, byte[] messageData, SmsAccessory accessory) {
        if (this.mDuplicateList == null) {
            this.mDuplicateList = new LinkedList<>();
        }
        SmsDuplicateInfo duplicateInfo = new SmsDuplicateInfo(messageID, accessory.getResponse(), messageData.length, messageData);
        if (this.mDuplicateList.size() >= this.mCheckedNum) {
            int exceedsNum = (this.mDuplicateList.size() - this.mCheckedNum) + 1;
            for (int i = 0; i < exceedsNum; i++) {
                this.mDuplicateList.poll();
            }
        }
        this.mDuplicateList.offer(duplicateInfo);
        if (duplicateInfo.mAck != 2 && this.mUseFile) {
            updateSmsDuplicateFile(duplicateInfo.mAck, duplicateInfo.mMessageID);
        }
    }

    public void updateSmsDuplicateFile(int sendAck, int messageId) {
        try {
            if (this.mDuplicateList.getLast().mMessageID == messageId) {
                if (this.mDuplicateList.getLast().mAck == 2) {
                    this.mDuplicateList.getLast().mAck = sendAck;
                }
                DataOutputStream dout = new DataOutputStream(this.mContext.openFileOutput(this.DUPLICATE_FILENAME, 0));
                for (int i = 0; i < this.mDuplicateList.size(); i++) {
                    dout.writeInt(this.mDuplicateList.get(i).mMessageID);
                    dout.writeInt(this.mDuplicateList.get(i).mAck);
                    dout.writeInt(this.mDuplicateList.get(i).mMessageDataLength);
                    dout.write(this.mDuplicateList.get(i).mMessageData);
                }
                dout.flush();
                dout.close();
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

    /* JADX INFO: Access modifiers changed from: protected */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SmsDuplicateInfo {
        public int mAck;
        public byte[] mMessageData;
        public int mMessageDataLength;
        public int mMessageID;

        protected SmsDuplicateInfo(int messageId, int sendAck, int length, byte[] messageData) {
            this.mMessageID = messageId;
            this.mAck = sendAck;
            this.mMessageDataLength = length;
            this.mMessageData = messageData;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class ResultJudgeDuplicate {
        public int mErrorCode;
        public boolean mIsReply;
        public boolean mIsSame;
        public boolean mResponse;

        public ResultJudgeDuplicate(boolean isSame, boolean response, int errorCode, boolean isReply) {
            this.mIsSame = false;
            this.mResponse = true;
            this.mErrorCode = -1;
            this.mIsReply = false;
            this.mIsSame = isSame;
            this.mResponse = response;
            this.mErrorCode = errorCode;
            this.mIsReply = isReply;
        }
    }

    public SmsAccessory SmsAccessory(String action, String permission, int response) {
        return new SmsAccessory(action, permission, 0);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SmsAccessory {
        private String mAction;
        private String mPermission;
        private int mResponse;

        public SmsAccessory(String action, String permission, int response) {
            this.mAction = action;
            this.mPermission = permission;
            this.mResponse = response;
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
}
