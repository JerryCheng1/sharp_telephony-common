package jp.co.sharp.android.internal.telephony;

import android.content.Context;
import android.os.Build;
import android.provider.Telephony.Carriers;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedList;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.ResultJudgeDuplicate;

public class EtwsDuplicate extends SmsDuplicate {
    private static boolean OUTPUT_DEBUG_LOG = false;
    private static final String TAG = "ETWS";
    private int mCheckedNum;
    private Context mContext;
    private LinkedList<SerialNumberInfo> mDuplicateList;
    private boolean mUseFile;

    public class SerialNumberInfo {
        public int mGeographicalScope;
        public boolean mIsPrimaryNotifyReceived = false;
        public boolean mIsSecondaryNotifyReceived = false;
        public int mSerialNumber;

        public SerialNumberInfo(int geographicalScope, int serialNumber) {
            this.mGeographicalScope = geographicalScope;
            this.mSerialNumber = serialNumber;
        }
    }

    public EtwsDuplicate(Context context, int checkedNum, boolean useFile) {
        super(context, checkedNum, useFile);
        this.mContext = context;
        this.mCheckedNum = checkedNum;
        this.mUseFile = useFile;
    }

    static {
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
        }
    }

    public ResultJudgeDuplicate checkEtwsDuplicate(SerialNumberInfo serialNumber, boolean etwsFormat) {
        boolean sameData = false;
        ResultJudgeDuplicate info = new ResultJudgeDuplicate(false, true, -1, true);
        if (this.mDuplicateList == null && this.mUseFile && !readDuplicateFile()) {
            Log.e(TAG, "readDuplicateFile() failed to read duplicate_file ");
            return info;
        }
        if (OUTPUT_DEBUG_LOG) {
            Log.d(TAG, "checkEtwsDuplicate(serialNumber.mGeographicalScope =" + serialNumber.mGeographicalScope + ")");
            Log.d(TAG, "checkEtwsDuplicate(serialNumber.mSerialNumber       =" + serialNumber.mSerialNumber + ")");
            Log.d(TAG, "checkEtwsDuplicate(etwsFormat =" + etwsFormat + ")");
        }
        if (this.mDuplicateList != null) {
            for (int listNum = 0; listNum < this.mDuplicateList.size(); listNum++) {
                SerialNumberInfo duplicateInfo = (SerialNumberInfo) this.mDuplicateList.get(listNum);
                if (OUTPUT_DEBUG_LOG) {
                    Log.d(TAG, "checkEtwsDuplicate(ListNum=" + listNum + ")");
                    Log.d(TAG, "checkEtwsDuplicate(duplicateInfo.mGeographicalScope =" + duplicateInfo.mGeographicalScope + ")");
                    Log.d(TAG, "checkEtwsDuplicate(duplicateInfo.mSerialNumber      =" + duplicateInfo.mSerialNumber + ")");
                    Log.d(TAG, "checkEtwsDuplicate(duplicateInfo.mIsPrimaryNotifyReceived   =" + duplicateInfo.mIsPrimaryNotifyReceived + ")");
                    Log.d(TAG, "checkEtwsDuplicate(duplicateInfo.mIsSecondaryNotifyReceived =" + duplicateInfo.mIsSecondaryNotifyReceived + ")");
                }
                if (duplicateInfo.mSerialNumber == serialNumber.mSerialNumber) {
                    sameData = etwsFormat ? duplicateInfo.mIsPrimaryNotifyReceived : duplicateInfo.mIsSecondaryNotifyReceived;
                } else {
                    sameData = false;
                    if (OUTPUT_DEBUG_LOG) {
                        Log.d(TAG, "sameData is false, notificate this ETWS.(ListNum=" + listNum + ")");
                    }
                }
                if (sameData) {
                    Log.d(TAG, "sameData is true, discard this ETWS.");
                    break;
                }
            }
            if (sameData) {
                info.mIsSame = sameData;
            }
        }
        return info;
    }

    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean readDuplicateFile() {
        boolean result = false;
        this.mDuplicateList = new LinkedList();
        try {
            DataInputStream din = new DataInputStream(this.mContext.openFileInput(this.DUPLICATE_FILENAME));
            while (true) {
                try {
                    int geographicalScope = din.readInt();
                    int serialNumber = din.readInt();
                    boolean isPrimaryNotifyReceived = din.readInt() == 1;
                    boolean isSecondaryNotifyReceived = din.readInt() == 1;
                    SerialNumberInfo info = new SerialNumberInfo(geographicalScope, serialNumber);
                    info.mIsPrimaryNotifyReceived = isPrimaryNotifyReceived;
                    info.mIsSecondaryNotifyReceived = isSecondaryNotifyReceived;
                    this.mDuplicateList.offer(info);
                } catch (EOFException e) {
                    Log.e(TAG, "checkEtwsDuplicate() duplicate_file reached EOF ");
                    return result;
                } finally {
                    din.close();
                    result = true;
                }
            }
        } catch (IOException e2) {
            Log.e(TAG, "checkEtwsDuplicate() failed to read duplicate_file ");
            e2.printStackTrace();
            return false;
        }
    }

    public void updateEtwsDuplicate(int geographicalScope, int serialNumber, boolean etwsFormat) {
        if (this.mDuplicateList == null) {
            this.mDuplicateList = new LinkedList();
        }
        if (OUTPUT_DEBUG_LOG) {
            Log.d(TAG, "updateEtwsDuplicate(serialNumber.geographicalScope =" + geographicalScope + ")");
            Log.d(TAG, "updateEtwsDuplicate(serialNumber.serialNumber      =" + serialNumber + ")");
            Log.d(TAG, "updateEtwsDuplicate(etwsFormat =" + etwsFormat + ")");
        }
        boolean isDuplicatedEtwsNotify = false;
        int isSameListNumber = -1;
        boolean isPrimaryRecieved = false;
        boolean isSecondryRecieved = false;
        for (int index = 0; index < this.mDuplicateList.size(); index++) {
            SerialNumberInfo duplicateInfo = (SerialNumberInfo) this.mDuplicateList.get(index);
            if (OUTPUT_DEBUG_LOG) {
                Log.d(TAG, "updateEtwsDuplicate(index=" + index + ")");
                Log.d(TAG, "updateEtwsDuplicate(duplicateInfo.mGeographicalScope =" + duplicateInfo.mGeographicalScope + ")");
                Log.d(TAG, "updateEtwsDuplicate(duplicateInfo.mSerialNumber      =" + duplicateInfo.mSerialNumber + ")");
                Log.d(TAG, "updateEtwsDuplicate(duplicateInfo.mIsPrimaryNotifyReceived   =" + duplicateInfo.mIsPrimaryNotifyReceived + ")");
                Log.d(TAG, "updateEtwsDuplicate(duplicateInfo.mIsSecondaryNotifyReceived =" + duplicateInfo.mIsSecondaryNotifyReceived + ")");
            }
            if (duplicateInfo.mSerialNumber != serialNumber) {
                isDuplicatedEtwsNotify = false;
            } else if (etwsFormat) {
                if (duplicateInfo.mIsPrimaryNotifyReceived) {
                    isDuplicatedEtwsNotify = true;
                    break;
                }
                isDuplicatedEtwsNotify = false;
                isPrimaryRecieved = true;
                isSecondryRecieved = duplicateInfo.mIsSecondaryNotifyReceived;
                isSameListNumber = index;
            } else if (duplicateInfo.mIsSecondaryNotifyReceived) {
                isDuplicatedEtwsNotify = true;
                break;
            } else {
                isDuplicatedEtwsNotify = false;
                isPrimaryRecieved = duplicateInfo.mIsPrimaryNotifyReceived;
                isSecondryRecieved = true;
                isSameListNumber = index;
            }
        }
        if (!isDuplicatedEtwsNotify) {
            if (isSameListNumber == -1) {
                if (etwsFormat) {
                    isPrimaryRecieved = true;
                    isSecondryRecieved = false;
                } else {
                    isPrimaryRecieved = false;
                    isSecondryRecieved = true;
                }
                SerialNumberInfo newSerialNumberInfo = new SerialNumberInfo(geographicalScope, serialNumber);
                newSerialNumberInfo.mIsPrimaryNotifyReceived = isPrimaryRecieved;
                newSerialNumberInfo.mIsSecondaryNotifyReceived = isSecondryRecieved;
                if (this.mDuplicateList.size() >= this.mCheckedNum) {
                    int exceedsNum = (this.mDuplicateList.size() - this.mCheckedNum) + 1;
                    for (int i = 0; i < exceedsNum; i++) {
                        this.mDuplicateList.poll();
                    }
                }
                this.mDuplicateList.offer(newSerialNumberInfo);
            } else {
                SerialNumberInfo modSerialNumberInfo = new SerialNumberInfo(geographicalScope, serialNumber);
                modSerialNumberInfo.mIsPrimaryNotifyReceived = isPrimaryRecieved;
                modSerialNumberInfo.mIsSecondaryNotifyReceived = isSecondryRecieved;
                this.mDuplicateList.set(isSameListNumber, modSerialNumberInfo);
            }
        }
        if (this.mUseFile) {
            updateEtwsDuplicateFile(geographicalScope, serialNumber);
        }
    }

    public void updateEtwsDuplicateFile(int geographicalScope, int serialNumber) {
        try {
            if (((SerialNumberInfo) this.mDuplicateList.getLast()).mSerialNumber == serialNumber) {
                DataOutputStream dout = new DataOutputStream(this.mContext.openFileOutput(this.DUPLICATE_FILENAME, 0));
                for (int i = 0; i < this.mDuplicateList.size(); i++) {
                    int i2;
                    dout.writeInt(((SerialNumberInfo) this.mDuplicateList.get(i)).mGeographicalScope);
                    dout.writeInt(((SerialNumberInfo) this.mDuplicateList.get(i)).mSerialNumber);
                    if (((SerialNumberInfo) this.mDuplicateList.get(i)).mIsPrimaryNotifyReceived) {
                        i2 = 1;
                    } else {
                        i2 = 0;
                    }
                    dout.writeInt(i2);
                    if (((SerialNumberInfo) this.mDuplicateList.get(i)).mIsSecondaryNotifyReceived) {
                        i2 = 1;
                    } else {
                        i2 = 0;
                    }
                    dout.writeInt(i2);
                }
                dout.flush();
                dout.close();
            } else if (OUTPUT_DEBUG_LOG) {
                Log.d(TAG, "updateEtwsDuplicateFile() MessageID is different ");
            }
        } catch (IOException e) {
            Log.e(TAG, "updateEtwsDuplicateFile() failed to update duplicate_file ");
            e.printStackTrace();
        } catch (NullPointerException e2) {
            Log.e(TAG, "updateEtwsDuplicateFile() the error by NullPointerException ");
        }
    }
}
