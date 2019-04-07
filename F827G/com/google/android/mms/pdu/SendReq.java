package com.google.android.mms.pdu;

import android.util.Log;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;

public class SendReq extends MultimediaMessagePdu {
    private static final String TAG = "SendReq";

    public SendReq() {
        try {
            setMessageType(128);
            setMmsVersion(18);
            setContentType(ContentType.MULTIPART_RELATED.getBytes());
            setFrom(new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes()));
            setTransactionId(generateTransactionId());
        } catch (InvalidHeaderValueException e) {
            Log.e(TAG, "Unexpected InvalidHeaderValueException.", e);
            throw new RuntimeException(e);
        }
    }

    SendReq(PduHeaders pduHeaders) {
        super(pduHeaders);
    }

    SendReq(PduHeaders pduHeaders, PduBody pduBody) {
        super(pduHeaders, pduBody);
    }

    public SendReq(byte[] bArr, EncodedStringValue encodedStringValue, int i, byte[] bArr2) throws InvalidHeaderValueException {
        setMessageType(128);
        setContentType(bArr);
        setFrom(encodedStringValue);
        setMmsVersion(i);
        setTransactionId(bArr2);
    }

    private byte[] generateTransactionId() {
        return ("T" + Long.toHexString(System.currentTimeMillis())).getBytes();
    }

    public void addBcc(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.appendEncodedStringValue(encodedStringValue, 129);
    }

    public void addCc(EncodedStringValue encodedStringValue) {
        this.mPduHeaders.appendEncodedStringValue(encodedStringValue, 130);
    }

    public EncodedStringValue[] getBcc() {
        return this.mPduHeaders.getEncodedStringValues(129);
    }

    public EncodedStringValue[] getCc() {
        return this.mPduHeaders.getEncodedStringValues(130);
    }

    public byte[] getContentType() {
        return this.mPduHeaders.getTextString(132);
    }

    public int getDeliveryReport() {
        return this.mPduHeaders.getOctet(134);
    }

    public long getExpiry() {
        return this.mPduHeaders.getLongInteger(136);
    }

    public byte[] getMessageClass() {
        return this.mPduHeaders.getTextString(138);
    }

    public long getMessageSize() {
        return this.mPduHeaders.getLongInteger(142);
    }

    public int getReadReport() {
        return this.mPduHeaders.getOctet(144);
    }

    public byte[] getTransactionId() {
        return this.mPduHeaders.getTextString(152);
    }

    public void setBcc(EncodedStringValue[] encodedStringValueArr) {
        this.mPduHeaders.setEncodedStringValues(encodedStringValueArr, 129);
    }

    public void setCc(EncodedStringValue[] encodedStringValueArr) {
        this.mPduHeaders.setEncodedStringValues(encodedStringValueArr, 130);
    }

    public void setContentType(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 132);
    }

    public void setDeliveryReport(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 134);
    }

    public void setExpiry(long j) {
        this.mPduHeaders.setLongInteger(j, 136);
    }

    public void setMessageClass(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 138);
    }

    public void setMessageSize(long j) {
        this.mPduHeaders.setLongInteger(j, 142);
    }

    public void setReadReport(int i) throws InvalidHeaderValueException {
        this.mPduHeaders.setOctet(i, 144);
    }

    public void setTo(EncodedStringValue[] encodedStringValueArr) {
        this.mPduHeaders.setEncodedStringValues(encodedStringValueArr, 151);
    }

    public void setTransactionId(byte[] bArr) {
        this.mPduHeaders.setTextString(bArr, 152);
    }
}
