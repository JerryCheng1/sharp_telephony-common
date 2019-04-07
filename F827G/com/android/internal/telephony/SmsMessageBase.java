package com.android.internal.telephony;

import android.provider.Telephony.Mms;
import com.android.internal.telephony.SmsConstants.MessageClass;
import java.util.Arrays;

public abstract class SmsMessageBase {
    protected String mEmailBody;
    protected String mEmailFrom;
    protected int mIndexOnIcc = -1;
    protected boolean mIsEmail;
    protected boolean mIsMwi;
    protected String mMessageBody;
    public int mMessageRef;
    protected boolean mMwiDontStore;
    protected boolean mMwiSense;
    protected SmsAddress mOriginatingAddress;
    protected byte[] mPdu;
    protected String mPseudoSubject;
    protected SmsAddress mRecipientAddress;
    protected String mScAddress;
    protected long mScTimeMillis;
    protected int mStatusOnIcc = -1;
    protected byte[] mUserData;
    protected SmsHeader mUserDataHeader;

    public static abstract class SubmitPduBase {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }
    }

    /* Access modifiers changed, original: protected */
    public void extractEmailAddressFromMessageBody() {
        String[] split = this.mMessageBody.split("( /)|( )", 2);
        if (split.length >= 2) {
            this.mEmailFrom = split[0];
            this.mEmailBody = split[1];
            this.mIsEmail = Mms.isEmailAddress(this.mEmailFrom);
        }
    }

    public String getDisplayMessageBody() {
        return this.mIsEmail ? this.mEmailBody : getMessageBody();
    }

    public String getDisplayOriginatingAddress() {
        return this.mIsEmail ? this.mEmailFrom : getOriginatingAddress();
    }

    public String getEmailBody() {
        return this.mEmailBody;
    }

    public String getEmailFrom() {
        return this.mEmailFrom;
    }

    public int getIndexOnIcc() {
        return this.mIndexOnIcc;
    }

    public String getMessageBody() {
        return this.mMessageBody;
    }

    public abstract MessageClass getMessageClass();

    public String getOriginatingAddress() {
        return this.mOriginatingAddress == null ? null : this.mOriginatingAddress.getAddressString();
    }

    public byte[] getPdu() {
        return this.mPdu;
    }

    public abstract int getProtocolIdentifier();

    public String getPseudoSubject() {
        return this.mPseudoSubject == null ? "" : this.mPseudoSubject;
    }

    public String getRecipientAddress() {
        return this.mRecipientAddress == null ? null : this.mRecipientAddress.getAddressString();
    }

    public String getServiceCenterAddress() {
        return this.mScAddress;
    }

    public abstract int getStatus();

    public int getStatusOnIcc() {
        return this.mStatusOnIcc;
    }

    public long getTimestampMillis() {
        return this.mScTimeMillis;
    }

    public byte[] getUserData() {
        return this.mUserData;
    }

    public SmsHeader getUserDataHeader() {
        return this.mUserDataHeader;
    }

    public abstract boolean isCphsMwiMessage();

    public boolean isEmail() {
        return this.mIsEmail;
    }

    public abstract boolean isMWIClearMessage();

    public abstract boolean isMWISetMessage();

    public abstract boolean isMwiDontStore();

    public abstract boolean isReplace();

    public abstract boolean isReplyPathPresent();

    public abstract boolean isStatusReportMessage();

    /* Access modifiers changed, original: protected */
    public void parseMessageBody() {
        if (this.mOriginatingAddress != null && this.mOriginatingAddress.couldBeEmailGateway()) {
            extractEmailAddressFromMessageBody();
        }
    }
}
