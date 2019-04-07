package com.google.android.mms.pdu;

import com.google.android.mms.InvalidHeaderValueException;

public class MultimediaMessagePdu extends GenericPdu {
    private PduBody mMessageBody;

    MultimediaMessagePdu(PduHeaders pduHeaders) {
        super(pduHeaders);
    }

    public MultimediaMessagePdu(PduHeaders pduHeaders, PduBody pduBody) {
        super(pduHeaders);
        this.mMessageBody = pduBody;
    }

    public void addTo(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.appendEncodedStringValue(encodedStringValue, 151);
    }

    public PduBody getBody() {
        return this.mMessageBody;
    }

    public long getDate() {
        return this.mPduHeaders.getLongInteger(133);
    }

    public int getPriority() {
        return this.mPduHeaders.getOctet(143);
    }

    public EncodedStringValue getSubject() {
        return this.mPduHeaders.getEncodedStringValue(150);
    }

    public EncodedStringValue[] getTo() {
        return this.mPduHeaders.getEncodedStringValues(151);
    }

    public void setBody(PduBody pduBody) {
        this.mMessageBody = pduBody;
    }

    public void setDate(long j) {
        this.mPduHeaders.setLongInteger(j, 133);
    }

    public void setPriority(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 143);
    }

    public void setSubject(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.setEncodedStringValue(encodedStringValue, 150);
    }
}
