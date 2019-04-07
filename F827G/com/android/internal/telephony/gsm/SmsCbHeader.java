package com.android.internal.telephony.gsm;

import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import java.util.Arrays;

class SmsCbHeader {
    static final int FORMAT_ETWS_PRIMARY = 3;
    static final int FORMAT_GSM = 1;
    static final int FORMAT_UMTS = 2;
    private static final int MESSAGE_TYPE_CBS_MESSAGE = 1;
    static final int PDU_HEADER_LENGTH = 6;
    private static final int PDU_LENGTH_ETWS = 56;
    private static final int PDU_LENGTH_GSM = 88;
    private final SmsCbCmasInfo mCmasInfo;
    private final int mDataCodingScheme;
    private final SmsCbEtwsInfo mEtwsInfo;
    private final int mFormat;
    private final int mGeographicalScope;
    private final int mMessageIdentifier;
    private final int mNrOfPages;
    private final int mPageIndex;
    private final int mSerialNumber;

    public SmsCbHeader(byte[] bArr) throws IllegalArgumentException {
        boolean z = true;
        if (bArr == null || bArr.length < 6) {
            throw new IllegalArgumentException("Illegal PDU");
        }
        if (bArr.length <= PDU_LENGTH_GSM) {
            this.mGeographicalScope = (bArr[0] & 192) >>> 6;
            this.mSerialNumber = ((bArr[0] & 255) << 8) | (bArr[1] & 255);
            this.mMessageIdentifier = ((bArr[2] & 255) << 8) | (bArr[3] & 255);
            if (!isEtwsMessage() || bArr.length > 56) {
                this.mFormat = 1;
                this.mDataCodingScheme = bArr[4] & 255;
                int i = (bArr[5] & 240) >>> 4;
                int i2 = bArr[5] & 15;
                if (i == 0 || i2 == 0 || i > i2) {
                    i2 = 1;
                    i = 1;
                }
                this.mPageIndex = i;
                this.mNrOfPages = i2;
            } else {
                this.mFormat = 3;
                this.mDataCodingScheme = -1;
                this.mPageIndex = -1;
                this.mNrOfPages = -1;
                boolean z2 = (bArr[4] & 1) != 0;
                if ((bArr[5] & 128) == 0) {
                    z = false;
                }
                this.mEtwsInfo = new SmsCbEtwsInfo((bArr[4] & 254) >>> 1, z2, z, bArr.length > 6 ? Arrays.copyOfRange(bArr, 6, bArr.length) : null);
                this.mCmasInfo = null;
                return;
            }
        }
        this.mFormat = 2;
        byte b = bArr[0];
        if (b != (byte) 1) {
            throw new IllegalArgumentException("Unsupported message type " + b);
        }
        this.mMessageIdentifier = ((bArr[1] & 255) << 8) | (bArr[2] & 255);
        this.mGeographicalScope = (bArr[3] & 192) >>> 6;
        this.mSerialNumber = ((bArr[3] & 255) << 8) | (bArr[4] & 255);
        this.mDataCodingScheme = bArr[5] & 255;
        this.mPageIndex = 1;
        this.mNrOfPages = 1;
        if (isEtwsMessage()) {
            this.mEtwsInfo = new SmsCbEtwsInfo(getEtwsWarningType(), isEtwsEmergencyUserAlert(), isEtwsPopupAlert(), null);
            this.mCmasInfo = null;
        } else if (isCmasMessage()) {
            int cmasMessageClass = getCmasMessageClass();
            int cmasSeverity = getCmasSeverity();
            int cmasUrgency = getCmasUrgency();
            int cmasCertainty = getCmasCertainty();
            this.mEtwsInfo = null;
            this.mCmasInfo = new SmsCbCmasInfo(cmasMessageClass, -1, -1, cmasSeverity, cmasUrgency, cmasCertainty);
        } else {
            this.mEtwsInfo = null;
            this.mCmasInfo = null;
        }
    }

    private int getCmasCertainty() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /*4371*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /*4373*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /*4375*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /*4377*/:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /*4372*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /*4374*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /*4376*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /*4378*/:
                return 1;
            default:
                return -1;
        }
    }

    private int getCmasMessageClass() {
        switch (this.mMessageIdentifier) {
            case 4370:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /*4371*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /*4372*/:
                return 1;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /*4373*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /*4374*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /*4375*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /*4376*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /*4377*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /*4378*/:
                return 2;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY /*4379*/:
                return 3;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST /*4380*/:
                return 4;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE /*4381*/:
                return 5;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE /*4382*/:
                return 6;
            default:
                return -1;
        }
    }

    private int getCmasSeverity() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /*4371*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /*4372*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /*4373*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /*4374*/:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /*4375*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /*4376*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /*4377*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /*4378*/:
                return 1;
            default:
                return -1;
        }
    }

    private int getCmasUrgency() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /*4371*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /*4372*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /*4375*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /*4376*/:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /*4373*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /*4374*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /*4377*/:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /*4378*/:
                return 1;
            default:
                return -1;
        }
    }

    private int getEtwsWarningType() {
        return this.mMessageIdentifier - 4352;
    }

    private boolean isCmasMessage() {
        return this.mMessageIdentifier >= 4370 && this.mMessageIdentifier <= SmsCbConstants.MESSAGE_ID_CMAS_LAST_IDENTIFIER;
    }

    private boolean isEtwsEmergencyUserAlert() {
        return (this.mSerialNumber & SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT) != 0;
    }

    private boolean isEtwsMessage() {
        return (this.mMessageIdentifier & SmsCbConstants.MESSAGE_ID_ETWS_TYPE_MASK) == 4352;
    }

    private boolean isEtwsPopupAlert() {
        return (this.mSerialNumber & 4096) != 0;
    }

    /* Access modifiers changed, original: 0000 */
    public SmsCbCmasInfo getCmasInfo() {
        return this.mCmasInfo;
    }

    /* Access modifiers changed, original: 0000 */
    public int getDataCodingScheme() {
        return this.mDataCodingScheme;
    }

    /* Access modifiers changed, original: 0000 */
    public SmsCbEtwsInfo getEtwsInfo() {
        return this.mEtwsInfo;
    }

    /* Access modifiers changed, original: 0000 */
    public int getGeographicalScope() {
        return this.mGeographicalScope;
    }

    /* Access modifiers changed, original: 0000 */
    public int getNumberOfPages() {
        return this.mNrOfPages;
    }

    /* Access modifiers changed, original: 0000 */
    public int getPageIndex() {
        return this.mPageIndex;
    }

    /* Access modifiers changed, original: 0000 */
    public int getSerialNumber() {
        return this.mSerialNumber;
    }

    /* Access modifiers changed, original: 0000 */
    public int getServiceCategory() {
        return this.mMessageIdentifier;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isEmergencyMessage() {
        return this.mMessageIdentifier >= 4352 && this.mMessageIdentifier <= SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isEtwsPrimaryNotification() {
        return this.mFormat == 3;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isUmtsFormat() {
        return this.mFormat == 2;
    }

    public String toString() {
        return "SmsCbHeader{GS=" + this.mGeographicalScope + ", serialNumber=0x" + Integer.toHexString(this.mSerialNumber) + ", messageIdentifier=0x" + Integer.toHexString(this.mMessageIdentifier) + ", DCS=0x" + Integer.toHexString(this.mDataCodingScheme) + ", page " + this.mPageIndex + " of " + this.mNrOfPages + '}';
    }
}
