package com.google.android.mms.pdu;

import com.google.android.mms.InvalidHeaderValueException;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class GenericPdu {
    PduHeaders mPduHeaders;

    public GenericPdu() {
        this.mPduHeaders = null;
        this.mPduHeaders = new PduHeaders();
    }

    public GenericPdu(PduHeaders headers) {
        this.mPduHeaders = null;
        this.mPduHeaders = headers;
    }

    public PduHeaders getPduHeaders() {
        return this.mPduHeaders;
    }

    public int getMessageType() {
        return this.mPduHeaders.getOctet(140);
    }

    public void setMessageType(int value) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(value, 140);
    }

    public int getMmsVersion() {
        return this.mPduHeaders.getOctet(141);
    }

    public void setMmsVersion(int value) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(value, 141);
    }

    public EncodedStringValue getFrom() {
        return this.mPduHeaders.getEncodedStringValue(137);
    }

    public void setFrom(EncodedStringValue value) {
        this.mPduHeaders.setEncodedStringValue(value, 137);
    }
}
