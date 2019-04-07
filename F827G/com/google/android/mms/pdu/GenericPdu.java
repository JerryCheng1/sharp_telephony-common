package com.google.android.mms.pdu;

import com.google.android.mms.InvalidHeaderValueException;

public class GenericPdu {
    PduHeaders mPduHeaders;

    public GenericPdu() {
        this.mPduHeaders = null;
        this.mPduHeaders = new PduHeaders();
    }

    GenericPdu(PduHeaders pduHeaders) {
        this.mPduHeaders = null;
        this.mPduHeaders = pduHeaders;
    }

    public EncodedStringValue getFrom() {
        return this.mPduHeaders.getEncodedStringValue(137);
    }

    public int getMessageType() {
        return this.mPduHeaders.getOctet(140);
    }

    public int getMmsVersion() {
        return this.mPduHeaders.getOctet(141);
    }

    /* Access modifiers changed, original: 0000 */
    public PduHeaders getPduHeaders() {
        return this.mPduHeaders;
    }

    public void setFrom(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.setEncodedStringValue(encodedStringValue, 137);
    }

    public void setMessageType(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 140);
    }

    public void setMmsVersion(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 141);
    }
}
