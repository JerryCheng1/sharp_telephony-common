package com.android.internal.telephony;

import android.content.ContentValues;
import android.database.Cursor;
import android.telephony.SmsMessage;
import com.android.internal.util.HexDump;
import java.util.Arrays;
import java.util.Date;

public final class InboundSmsTracker {
    private static final int DEST_PORT_FLAG_3GPP = 131072;
    private static final int DEST_PORT_FLAG_3GPP2 = 262144;
    private static final int DEST_PORT_FLAG_3GPP2_WAP_PDU = 524288;
    private static final int DEST_PORT_FLAG_NO_PORT = 65536;
    private static final int DEST_PORT_MASK = 65535;
    private final String mAddress;
    private String mDeleteWhere;
    private String[] mDeleteWhereArgs;
    private final int mDestPort;
    private final boolean mIs3gpp2;
    private final boolean mIs3gpp2WapPdu;
    private final int mMessageCount;
    private final byte[] mPdu;
    private final int mReferenceNumber;
    private final int mSequenceNumber;
    private final long mTimestamp;

    InboundSmsTracker(Cursor cursor, boolean z) {
        this.mPdu = HexDump.hexStringToByteArray(cursor.getString(0));
        if (cursor.isNull(2)) {
            this.mDestPort = -1;
            this.mIs3gpp2 = z;
            this.mIs3gpp2WapPdu = false;
        } else {
            int i = cursor.getInt(2);
            if ((DEST_PORT_FLAG_3GPP & i) != 0) {
                this.mIs3gpp2 = false;
            } else if ((262144 & i) != 0) {
                this.mIs3gpp2 = true;
            } else {
                this.mIs3gpp2 = z;
            }
            this.mIs3gpp2WapPdu = (DEST_PORT_FLAG_3GPP2_WAP_PDU & i) != 0;
            this.mDestPort = getRealDestPort(i);
        }
        this.mTimestamp = cursor.getLong(3);
        if (cursor.isNull(5)) {
            long j = cursor.getLong(7);
            this.mAddress = null;
            this.mReferenceNumber = -1;
            this.mSequenceNumber = getIndexOffset();
            this.mMessageCount = 1;
            this.mDeleteWhere = "_id=?";
            this.mDeleteWhereArgs = new String[]{Long.toString(j)};
            return;
        }
        this.mAddress = cursor.getString(6);
        this.mReferenceNumber = cursor.getInt(4);
        this.mMessageCount = cursor.getInt(5);
        this.mSequenceNumber = cursor.getInt(1);
        int indexOffset = this.mSequenceNumber - getIndexOffset();
        if (indexOffset < 0 || indexOffset >= this.mMessageCount) {
            throw new IllegalArgumentException("invalid PDU sequence " + this.mSequenceNumber + " of " + this.mMessageCount);
        }
        this.mDeleteWhere = "address=? AND reference_number=? AND count=?";
        this.mDeleteWhereArgs = new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
    }

    public InboundSmsTracker(byte[] bArr, long j, int i, boolean z, String str, int i2, int i3, int i4, boolean z2) {
        this.mPdu = bArr;
        this.mTimestamp = j;
        this.mDestPort = i;
        this.mIs3gpp2 = z;
        this.mIs3gpp2WapPdu = z2;
        this.mAddress = str;
        this.mReferenceNumber = i2;
        this.mSequenceNumber = i3;
        this.mMessageCount = i4;
    }

    InboundSmsTracker(byte[] bArr, long j, int i, boolean z, boolean z2) {
        this.mPdu = bArr;
        this.mTimestamp = j;
        this.mDestPort = i;
        this.mIs3gpp2 = z;
        this.mIs3gpp2WapPdu = z2;
        this.mAddress = null;
        this.mReferenceNumber = -1;
        this.mSequenceNumber = getIndexOffset();
        this.mMessageCount = 1;
    }

    static int getRealDestPort(int i) {
        return (DEST_PORT_FLAG_NO_PORT & i) != 0 ? -1 : 65535 & i;
    }

    /* Access modifiers changed, original: 0000 */
    public String getAddress() {
        return this.mAddress;
    }

    /* Access modifiers changed, original: 0000 */
    public ContentValues getContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("pdu", HexDump.toHexString(this.mPdu));
        contentValues.put("date", Long.valueOf(this.mTimestamp));
        int i = this.mDestPort == -1 ? DEST_PORT_FLAG_NO_PORT : this.mDestPort & 65535;
        i = this.mIs3gpp2 ? i | 262144 : i | DEST_PORT_FLAG_3GPP;
        if (this.mIs3gpp2WapPdu) {
            i |= DEST_PORT_FLAG_3GPP2_WAP_PDU;
        }
        contentValues.put("destination_port", Integer.valueOf(i));
        if (this.mAddress != null) {
            contentValues.put("address", this.mAddress);
            contentValues.put("reference_number", Integer.valueOf(this.mReferenceNumber));
            contentValues.put("sequence", Integer.valueOf(this.mSequenceNumber));
            contentValues.put("count", Integer.valueOf(this.mMessageCount));
        }
        return contentValues;
    }

    /* Access modifiers changed, original: 0000 */
    public String getDeleteWhere() {
        return this.mDeleteWhere;
    }

    /* Access modifiers changed, original: 0000 */
    public String[] getDeleteWhereArgs() {
        return this.mDeleteWhereArgs;
    }

    /* Access modifiers changed, original: 0000 */
    public int getDestPort() {
        return this.mDestPort;
    }

    /* Access modifiers changed, original: 0000 */
    public String getFormat() {
        return this.mIs3gpp2 ? SmsMessage.FORMAT_3GPP2 : SmsMessage.FORMAT_3GPP;
    }

    /* Access modifiers changed, original: 0000 */
    public int getIndexOffset() {
        return (this.mIs3gpp2 && this.mIs3gpp2WapPdu) ? 0 : 1;
    }

    /* Access modifiers changed, original: 0000 */
    public int getMessageCount() {
        return this.mMessageCount;
    }

    /* Access modifiers changed, original: 0000 */
    public byte[] getPdu() {
        return this.mPdu;
    }

    /* Access modifiers changed, original: 0000 */
    public int getReferenceNumber() {
        return this.mReferenceNumber;
    }

    /* Access modifiers changed, original: 0000 */
    public int getSequenceNumber() {
        return this.mSequenceNumber;
    }

    /* Access modifiers changed, original: 0000 */
    public long getTimestamp() {
        return this.mTimestamp;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean is3gpp2() {
        return this.mIs3gpp2;
    }

    /* Access modifiers changed, original: 0000 */
    public void setDeleteWhere(String str, String[] strArr) {
        this.mDeleteWhere = str;
        this.mDeleteWhereArgs = strArr;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("SmsTracker{timestamp=");
        stringBuilder.append(new Date(this.mTimestamp));
        stringBuilder.append(" destPort=").append(this.mDestPort);
        stringBuilder.append(" is3gpp2=").append(this.mIs3gpp2);
        if (this.mAddress != null) {
            stringBuilder.append(" address=").append(this.mAddress);
            stringBuilder.append(" refNumber=").append(this.mReferenceNumber);
            stringBuilder.append(" seqNumber=").append(this.mSequenceNumber);
            stringBuilder.append(" msgCount=").append(this.mMessageCount);
        }
        if (this.mDeleteWhere != null) {
            stringBuilder.append(" deleteWhere(").append(this.mDeleteWhere);
            stringBuilder.append(") deleteArgs=(").append(Arrays.toString(this.mDeleteWhereArgs));
            stringBuilder.append(')');
        }
        stringBuilder.append('}');
        return stringBuilder.toString();
    }
}
