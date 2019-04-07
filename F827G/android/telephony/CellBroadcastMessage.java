package android.telephony;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.provider.Telephony.CellBroadcasts;
import android.text.format.DateUtils;

public class CellBroadcastMessage implements Parcelable {
    public static final Creator<CellBroadcastMessage> CREATOR = new Creator<CellBroadcastMessage>() {
        public CellBroadcastMessage createFromParcel(Parcel parcel) {
            return new CellBroadcastMessage(parcel, null);
        }

        public CellBroadcastMessage[] newArray(int i) {
            return new CellBroadcastMessage[i];
        }
    };
    public static final String SMS_CB_MESSAGE_EXTRA = "com.android.cellbroadcastreceiver.SMS_CB_MESSAGE";
    private final long mDeliveryTime;
    private boolean mIsRead;
    private final SmsCbMessage mSmsCbMessage;
    private int mSubId;

    private CellBroadcastMessage(Parcel parcel) {
        boolean z = false;
        this.mSubId = 0;
        this.mSmsCbMessage = new SmsCbMessage(parcel);
        this.mDeliveryTime = parcel.readLong();
        if (parcel.readInt() != 0) {
            z = true;
        }
        this.mIsRead = z;
        this.mSubId = parcel.readInt();
    }

    /* synthetic */ CellBroadcastMessage(Parcel parcel, AnonymousClass1 anonymousClass1) {
        this(parcel);
    }

    public CellBroadcastMessage(SmsCbMessage smsCbMessage) {
        this.mSubId = 0;
        this.mSmsCbMessage = smsCbMessage;
        this.mDeliveryTime = System.currentTimeMillis();
        this.mIsRead = false;
        this.mSubId = SubscriptionManager.getDefaultSmsSubId();
    }

    private CellBroadcastMessage(SmsCbMessage smsCbMessage, long j, boolean z) {
        this.mSubId = 0;
        this.mSmsCbMessage = smsCbMessage;
        this.mDeliveryTime = j;
        this.mIsRead = z;
        this.mSubId = SubscriptionManager.getDefaultSmsSubId();
    }

    public static CellBroadcastMessage createFromCursor(Cursor cursor) {
        SmsCbCmasInfo smsCbCmasInfo;
        int i = cursor.getInt(cursor.getColumnIndexOrThrow(CellBroadcasts.GEOGRAPHICAL_SCOPE));
        int i2 = cursor.getInt(cursor.getColumnIndexOrThrow(CellBroadcasts.SERIAL_NUMBER));
        int i3 = cursor.getInt(cursor.getColumnIndexOrThrow(CellBroadcasts.SERVICE_CATEGORY));
        String string = cursor.getString(cursor.getColumnIndexOrThrow(CellBroadcasts.LANGUAGE_CODE));
        String string2 = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        int i4 = cursor.getInt(cursor.getColumnIndexOrThrow(CellBroadcasts.MESSAGE_FORMAT));
        int i5 = cursor.getInt(cursor.getColumnIndexOrThrow("priority"));
        int columnIndex = cursor.getColumnIndex(CellBroadcasts.PLMN);
        String string3 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? null : cursor.getString(columnIndex);
        int columnIndex2 = cursor.getColumnIndex(CellBroadcasts.LAC);
        columnIndex2 = (columnIndex2 == -1 || cursor.isNull(columnIndex2)) ? -1 : cursor.getInt(columnIndex2);
        int columnIndex3 = cursor.getColumnIndex("cid");
        columnIndex3 = (columnIndex3 == -1 || cursor.isNull(columnIndex3)) ? -1 : cursor.getInt(columnIndex3);
        SmsCbLocation smsCbLocation = new SmsCbLocation(string3, columnIndex2, columnIndex3);
        columnIndex = cursor.getColumnIndex(CellBroadcasts.ETWS_WARNING_TYPE);
        SmsCbEtwsInfo smsCbEtwsInfo = (columnIndex == -1 || cursor.isNull(columnIndex)) ? null : new SmsCbEtwsInfo(cursor.getInt(columnIndex), false, false, null);
        columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_MESSAGE_CLASS);
        if (columnIndex == -1 || cursor.isNull(columnIndex)) {
            smsCbCmasInfo = null;
        } else {
            columnIndex2 = cursor.getInt(columnIndex);
            columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_CATEGORY);
            columnIndex3 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? -1 : cursor.getInt(columnIndex);
            columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_RESPONSE_TYPE);
            int i6 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? -1 : cursor.getInt(columnIndex);
            columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_SEVERITY);
            int i7 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? -1 : cursor.getInt(columnIndex);
            columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_URGENCY);
            int i8 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? -1 : cursor.getInt(columnIndex);
            columnIndex = cursor.getColumnIndex(CellBroadcasts.CMAS_CERTAINTY);
            int i9 = (columnIndex == -1 || cursor.isNull(columnIndex)) ? -1 : cursor.getInt(columnIndex);
            smsCbCmasInfo = new SmsCbCmasInfo(columnIndex2, columnIndex3, i6, i7, i8, i9);
        }
        return new CellBroadcastMessage(new SmsCbMessage(i4, i, i2, smsCbLocation, i3, string, string2, i5, smsCbEtwsInfo, smsCbCmasInfo), cursor.getLong(cursor.getColumnIndexOrThrow("date")), cursor.getInt(cursor.getColumnIndexOrThrow("read")) != 0);
    }

    public int describeContents() {
        return 0;
    }

    public int getCmasMessageClass() {
        return this.mSmsCbMessage.isCmasMessage() ? this.mSmsCbMessage.getCmasWarningInfo().getMessageClass() : -1;
    }

    public SmsCbCmasInfo getCmasWarningInfo() {
        return this.mSmsCbMessage.getCmasWarningInfo();
    }

    public ContentValues getContentValues() {
        ContentValues contentValues = new ContentValues(16);
        SmsCbMessage smsCbMessage = this.mSmsCbMessage;
        contentValues.put(CellBroadcasts.GEOGRAPHICAL_SCOPE, Integer.valueOf(smsCbMessage.getGeographicalScope()));
        SmsCbLocation location = smsCbMessage.getLocation();
        if (location.getPlmn() != null) {
            contentValues.put(CellBroadcasts.PLMN, location.getPlmn());
        }
        if (location.getLac() != -1) {
            contentValues.put(CellBroadcasts.LAC, Integer.valueOf(location.getLac()));
        }
        if (location.getCid() != -1) {
            contentValues.put("cid", Integer.valueOf(location.getCid()));
        }
        contentValues.put(CellBroadcasts.SERIAL_NUMBER, Integer.valueOf(smsCbMessage.getSerialNumber()));
        contentValues.put(CellBroadcasts.SERVICE_CATEGORY, Integer.valueOf(smsCbMessage.getServiceCategory()));
        contentValues.put(CellBroadcasts.LANGUAGE_CODE, smsCbMessage.getLanguageCode());
        contentValues.put("body", smsCbMessage.getMessageBody());
        contentValues.put("date", Long.valueOf(this.mDeliveryTime));
        contentValues.put("read", Boolean.valueOf(this.mIsRead));
        contentValues.put(CellBroadcasts.MESSAGE_FORMAT, Integer.valueOf(smsCbMessage.getMessageFormat()));
        contentValues.put("priority", Integer.valueOf(smsCbMessage.getMessagePriority()));
        SmsCbEtwsInfo etwsWarningInfo = this.mSmsCbMessage.getEtwsWarningInfo();
        if (etwsWarningInfo != null) {
            contentValues.put(CellBroadcasts.ETWS_WARNING_TYPE, Integer.valueOf(etwsWarningInfo.getWarningType()));
        }
        SmsCbCmasInfo cmasWarningInfo = this.mSmsCbMessage.getCmasWarningInfo();
        if (cmasWarningInfo != null) {
            contentValues.put(CellBroadcasts.CMAS_MESSAGE_CLASS, Integer.valueOf(cmasWarningInfo.getMessageClass()));
            contentValues.put(CellBroadcasts.CMAS_CATEGORY, Integer.valueOf(cmasWarningInfo.getCategory()));
            contentValues.put(CellBroadcasts.CMAS_RESPONSE_TYPE, Integer.valueOf(cmasWarningInfo.getResponseType()));
            contentValues.put(CellBroadcasts.CMAS_SEVERITY, Integer.valueOf(cmasWarningInfo.getSeverity()));
            contentValues.put(CellBroadcasts.CMAS_URGENCY, Integer.valueOf(cmasWarningInfo.getUrgency()));
            contentValues.put(CellBroadcasts.CMAS_CERTAINTY, Integer.valueOf(cmasWarningInfo.getCertainty()));
        }
        return contentValues;
    }

    public String getDateString(Context context) {
        return DateUtils.formatDateTime(context, this.mDeliveryTime, 527121);
    }

    public long getDeliveryTime() {
        return this.mDeliveryTime;
    }

    public SmsCbEtwsInfo getEtwsWarningInfo() {
        return this.mSmsCbMessage.getEtwsWarningInfo();
    }

    public String getLanguageCode() {
        return this.mSmsCbMessage.getLanguageCode();
    }

    public String getMessageBody() {
        return this.mSmsCbMessage.getMessageBody();
    }

    public int getSerialNumber() {
        return this.mSmsCbMessage.getSerialNumber();
    }

    public int getServiceCategory() {
        return this.mSmsCbMessage.getServiceCategory();
    }

    public String getSpokenDateString(Context context) {
        return DateUtils.formatDateTime(context, this.mDeliveryTime, 17);
    }

    public int getSubId() {
        return this.mSubId;
    }

    public boolean isCmasMessage() {
        return this.mSmsCbMessage.isCmasMessage();
    }

    public boolean isEmergencyAlertMessage() {
        return this.mSmsCbMessage.isEmergencyMessage();
    }

    public boolean isEtwsEmergencyUserAlert() {
        SmsCbEtwsInfo etwsWarningInfo = this.mSmsCbMessage.getEtwsWarningInfo();
        return etwsWarningInfo != null && etwsWarningInfo.isEmergencyUserAlert();
    }

    public boolean isEtwsMessage() {
        return this.mSmsCbMessage.isEtwsMessage();
    }

    public boolean isEtwsPopupAlert() {
        SmsCbEtwsInfo etwsWarningInfo = this.mSmsCbMessage.getEtwsWarningInfo();
        return etwsWarningInfo != null && etwsWarningInfo.isPopupAlert();
    }

    public boolean isEtwsTestMessage() {
        SmsCbEtwsInfo etwsWarningInfo = this.mSmsCbMessage.getEtwsWarningInfo();
        return etwsWarningInfo != null && etwsWarningInfo.getWarningType() == 3;
    }

    public boolean isPublicAlertMessage() {
        return this.mSmsCbMessage.isEmergencyMessage();
    }

    public boolean isRead() {
        return this.mIsRead;
    }

    public void setIsRead(boolean z) {
        this.mIsRead = z;
    }

    public void setSubId(int i) {
        this.mSubId = i;
    }

    public void writeToParcel(Parcel parcel, int i) {
        this.mSmsCbMessage.writeToParcel(parcel, i);
        parcel.writeLong(this.mDeliveryTime);
        parcel.writeInt(this.mIsRead ? 1 : 0);
        parcel.writeInt(this.mSubId);
    }
}
