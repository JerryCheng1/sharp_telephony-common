package com.google.android.mms.pdu;

import com.google.android.mms.InvalidHeaderValueException;

public class NotificationInd extends GenericPdu {
    public NotificationInd() throws InvalidHeaderValueException {
        setMessageType(130);
    }

    NotificationInd(PduHeaders pduHeaders) {
        super(pduHeaders);
    }

    public int getContentClass() {
        return this.mPduHeaders.getOctet(PduHeaders.CONTENT_CLASS);
    }

    public byte[] getContentLocation() {
        return this.mPduHeaders.getTextString(131);
    }

    public int getDeliveryReport() {
        return this.mPduHeaders.getOctet(134);
    }

    public long getExpiry() {
        return this.mPduHeaders.getLongInteger(136);
    }

    public EncodedStringValue getFrom() {
        return this.mPduHeaders.getEncodedStringValue(137);
    }

    public byte[] getMessageClass() {
        return this.mPduHeaders.getTextString(138);
    }

    public long getMessageSize() {
        return this.mPduHeaders.getLongInteger(142);
    }

    public EncodedStringValue getSubject() {
        return this.mPduHeaders.getEncodedStringValue(150);
    }

    public byte[] getTransactionId() {
        return this.mPduHeaders.getTextString(152);
    }

    public void setContentClass(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, PduHeaders.CONTENT_CLASS);
    }

    public void setContentLocation(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 131);
    }

    public void setDeliveryReport(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 134);
    }

    public void setExpiry(long j) {
        this.mPduHeaders.setLongInteger(j, 136);
    }

    public void setFrom(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.setEncodedStringValue(encodedStringValue, 137);
    }

    public void setMessageClass(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 138);
    }

    public void setMessageSize(long j) {
        this.mPduHeaders.setLongInteger(j, 142);
    }

    public void setSubject(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.setEncodedStringValue(encodedStringValue, 150);
    }

    public void setTransactionId(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 152);
    }
}
