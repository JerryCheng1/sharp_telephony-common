package android.telephony;

import android.content.res.Resources;
import android.os.Binder;
import android.os.Parcel;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import java.util.ArrayList;
import java.util.Arrays;

public class SmsMessage {
    public static final int ENCODING_16BIT = 3;
    public static final int ENCODING_7BIT = 1;
    public static final int ENCODING_8BIT = 2;
    public static final int ENCODING_KSC5601 = 4;
    public static final int ENCODING_UNKNOWN = 0;
    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";
    private static final String LOG_TAG = "SmsMessage";
    public static final int MAX_USER_DATA_BYTES = 140;
    public static final int MAX_USER_DATA_BYTES_WITH_HEADER = 134;
    public static final int MAX_USER_DATA_SEPTETS = 160;
    public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;
    private static boolean mIsNoEmsSupportConfigListLoaded = false;
    private static NoEmsSupportConfig[] mNoEmsSupportConfigList = null;
    private int mSubId = 0;
    public SmsMessageBase mWrappedSmsMessage;

    /* renamed from: android.telephony.SmsMessage$1 */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass = new int[com.android.internal.telephony.SmsConstants.MessageClass.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_0.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_1.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_2.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_3.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    public enum MessageClass {
        UNKNOWN,
        CLASS_0,
        CLASS_1,
        CLASS_2,
        CLASS_3
    }

    private static class NoEmsSupportConfig {
        String mGid1;
        boolean mIsPrefix;
        String mOperatorNumber;

        public NoEmsSupportConfig(String[] strArr) {
            this.mOperatorNumber = strArr[0];
            this.mIsPrefix = "prefix".equals(strArr[1]);
            this.mGid1 = strArr.length > 2 ? strArr[2] : null;
        }

        public String toString() {
            return "NoEmsSupportConfig { mOperatorNumber = " + this.mOperatorNumber + ", mIsPrefix = " + this.mIsPrefix + ", mGid1 = " + this.mGid1 + " }";
        }
    }

    public static class SubmitPdu {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        protected SubmitPdu(SubmitPduBase submitPduBase) {
            this.encodedMessage = submitPduBase.encodedMessage;
            this.encodedScAddress = submitPduBase.encodedScAddress;
        }

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }
    }

    private SmsMessage(SmsMessageBase smsMessageBase) {
        this.mWrappedSmsMessage = smsMessageBase;
    }

    public static int[] calculateLength(CharSequence charSequence, boolean z) {
        TextEncodingDetails calculateLength = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(charSequence, z) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(charSequence, z);
        return new int[]{calculateLength.msgCount, calculateLength.codeUnitCount, calculateLength.codeUnitsRemaining, calculateLength.codeUnitSize};
    }

    public static int[] calculateLength(String str, boolean z) {
        return calculateLength((CharSequence) str, z);
    }

    public static SmsMessage createFromEfRecord(int i, byte[] bArr) {
        SmsMessageBase createFromEfRecord = isCdmaVoice() ? com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(i, bArr) : com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(i, bArr);
        return createFromEfRecord != null ? new SmsMessage(createFromEfRecord) : null;
    }

    public static SmsMessage createFromEfRecord(int i, byte[] bArr, int i2) {
        SmsMessageBase createFromEfRecord = isCdmaVoice(i2) ? com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(i, bArr) : com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(i, bArr);
        return createFromEfRecord != null ? new SmsMessage(createFromEfRecord) : null;
    }

    public static SmsMessage createFromPdu(byte[] bArr) {
        int currentPhoneType = TelephonyManager.getDefault().getCurrentPhoneType();
        SmsMessage createFromPdu = createFromPdu(bArr, 2 == currentPhoneType ? FORMAT_3GPP2 : FORMAT_3GPP);
        if (createFromPdu != null && createFromPdu.mWrappedSmsMessage != null) {
            return createFromPdu;
        }
        return createFromPdu(bArr, 2 == currentPhoneType ? FORMAT_3GPP : FORMAT_3GPP2);
    }

    public static SmsMessage createFromPdu(byte[] bArr, String str) {
        SmsMessageBase createFromPdu;
        if (FORMAT_3GPP2.equals(str)) {
            createFromPdu = com.android.internal.telephony.cdma.SmsMessage.createFromPdu(bArr);
        } else if (FORMAT_3GPP.equals(str)) {
            createFromPdu = com.android.internal.telephony.gsm.SmsMessage.createFromPdu(bArr);
        } else {
            Rlog.e(LOG_TAG, "createFromPdu(): unsupported message format " + str);
            return null;
        }
        return new SmsMessage(createFromPdu);
    }

    public static ArrayList<String> fragmentText(String str) {
        int i;
        int i2 = 0;
        TextEncodingDetails calculateLength = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(str, false) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(str, false);
        if (calculateLength.codeUnitSize == 1) {
            i = (calculateLength.languageTable == 0 || calculateLength.languageShiftTable == 0) ? (calculateLength.languageTable == 0 && calculateLength.languageShiftTable == 0) ? 0 : 4 : 7;
            if (calculateLength.msgCount > 1) {
                i += 6;
            }
            if (i != 0) {
                i++;
            }
            i = 160 - i;
        } else if (calculateLength.msgCount > 1) {
            i = 134;
            if (!hasEmsSupport() && calculateLength.msgCount < 10) {
                i = 132;
            }
        } else {
            i = 140;
        }
        CharSequence charSequence = null;
        if (Resources.getSystem().getBoolean(17957010)) {
            charSequence = Sms7BitEncodingTranslator.translate(str);
        }
        if (!TextUtils.isEmpty(charSequence)) {
            CharSequence str2 = charSequence;
        }
        int length = str2.length();
        ArrayList arrayList = new ArrayList(calculateLength.msgCount);
        while (i2 < length) {
            int min = calculateLength.codeUnitSize == 1 ? (useCdmaFormatForMoSms() && calculateLength.msgCount == 1) ? Math.min(i, length - i2) + i2 : GsmAlphabet.findGsmSeptetLimitIndex(str2, i2, i, calculateLength.languageTable, calculateLength.languageShiftTable) : Math.min(i / 2, length - i2) + i2;
            if (min <= i2 || min > length) {
                Rlog.e(LOG_TAG, "fragmentText failed (" + i2 + " >= " + min + " or " + min + " >= " + length + ")");
                break;
            }
            arrayList.add(str2.substring(i2, min));
            i2 = min;
        }
        return arrayList;
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z) {
        return new SubmitPdu(useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(str, str2, str3, z, null) : com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(str, str2, str3, z));
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, int i) {
        return new SubmitPdu(useCdmaFormatForMoSms(i) ? com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(str, str2, str3, z, null) : com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(str, str2, str3, z));
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, short s, byte[] bArr, boolean z) {
        return new SubmitPdu(useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(str, str2, (int) s, bArr, z) : com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(str, str2, (int) s, bArr, z));
    }

    public static int getTPLayerLengthForPDU(String str) {
        return isCdmaVoice() ? com.android.internal.telephony.cdma.SmsMessage.getTPLayerLengthForPDU(str) : com.android.internal.telephony.gsm.SmsMessage.getTPLayerLengthForPDU(str);
    }

    public static boolean hasEmsSupport() {
        if (isNoEmsSupportConfigListExisted()) {
            long clearCallingIdentity = Binder.clearCallingIdentity();
            try {
                String simOperator = TelephonyManager.getDefault().getSimOperator();
                String groupIdLevel1 = TelephonyManager.getDefault().getGroupIdLevel1();
                for (NoEmsSupportConfig noEmsSupportConfig : mNoEmsSupportConfigList) {
                    if (simOperator.startsWith(noEmsSupportConfig.mOperatorNumber)) {
                        if (TextUtils.isEmpty(noEmsSupportConfig.mGid1)) {
                            return false;
                        }
                        if (!TextUtils.isEmpty(noEmsSupportConfig.mGid1) && noEmsSupportConfig.mGid1.equalsIgnoreCase(groupIdLevel1)) {
                            return false;
                        }
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(clearCallingIdentity);
            }
        }
        return true;
    }

    private static boolean isCdmaVoice() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType();
    }

    private static boolean isCdmaVoice(int i) {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType(i);
    }

    private static boolean isNoEmsSupportConfigListExisted() {
        if (!mIsNoEmsSupportConfigListLoaded) {
            Resources system = Resources.getSystem();
            if (system != null) {
                String[] stringArray = system.getStringArray(17236026);
                if (stringArray != null && stringArray.length > 0) {
                    mNoEmsSupportConfigList = new NoEmsSupportConfig[stringArray.length];
                    for (int i = 0; i < stringArray.length; i++) {
                        mNoEmsSupportConfigList[i] = new NoEmsSupportConfig(stringArray[i].split(";"));
                    }
                }
                mIsNoEmsSupportConfigListLoaded = true;
            }
        }
        return (mNoEmsSupportConfigList == null || mNoEmsSupportConfigList.length == 0) ? false : true;
    }

    public static SmsMessage newFromCMT(String[] strArr) {
        return new SmsMessage(com.android.internal.telephony.gsm.SmsMessage.newFromCMT(strArr));
    }

    public static SmsMessage newFromParcel(Parcel parcel) {
        return new SmsMessage(com.android.internal.telephony.cdma.SmsMessage.newFromParcel(parcel));
    }

    public static boolean shouldAppendPageNumberAsPrefix() {
        if (!isNoEmsSupportConfigListExisted()) {
            return false;
        }
        long clearCallingIdentity = Binder.clearCallingIdentity();
        try {
            String simOperator = TelephonyManager.getDefault().getSimOperator();
            String groupIdLevel1 = TelephonyManager.getDefault().getGroupIdLevel1();
            for (NoEmsSupportConfig noEmsSupportConfig : mNoEmsSupportConfigList) {
                if (simOperator.startsWith(noEmsSupportConfig.mOperatorNumber) && (TextUtils.isEmpty(noEmsSupportConfig.mGid1) || (!TextUtils.isEmpty(noEmsSupportConfig.mGid1) && noEmsSupportConfig.mGid1.equalsIgnoreCase(groupIdLevel1)))) {
                    return noEmsSupportConfig.mIsPrefix;
                }
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    private static boolean useCdmaFormatForMoSms() {
        SmsManager smsManagerForSubscriptionId = SmsManager.getSmsManagerForSubscriptionId(SubscriptionManager.getDefaultSmsSubId());
        return !smsManagerForSubscriptionId.isImsSmsSupported() ? isCdmaVoice() : FORMAT_3GPP2.equals(smsManagerForSubscriptionId.getImsSmsFormat());
    }

    private static boolean useCdmaFormatForMoSms(int i) {
        SmsManager smsManagerForSubscriptionId = SmsManager.getSmsManagerForSubscriptionId(i);
        return !smsManagerForSubscriptionId.isImsSmsSupported() ? isCdmaVoice(i) : FORMAT_3GPP2.equals(smsManagerForSubscriptionId.getImsSmsFormat());
    }

    public String getDisplayMessageBody() {
        return this.mWrappedSmsMessage.getDisplayMessageBody();
    }

    public String getDisplayOriginatingAddress() {
        return this.mWrappedSmsMessage.getDisplayOriginatingAddress();
    }

    public String getEmailBody() {
        return this.mWrappedSmsMessage.getEmailBody();
    }

    public String getEmailFrom() {
        return this.mWrappedSmsMessage.getEmailFrom();
    }

    public int getIndexOnIcc() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    @Deprecated
    public int getIndexOnSim() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    public String getMessageBody() {
        return this.mWrappedSmsMessage.getMessageBody();
    }

    public MessageClass getMessageClass() {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[this.mWrappedSmsMessage.getMessageClass().ordinal()]) {
            case 1:
                return MessageClass.CLASS_0;
            case 2:
                return MessageClass.CLASS_1;
            case 3:
                return MessageClass.CLASS_2;
            case 4:
                return MessageClass.CLASS_3;
            default:
                return MessageClass.UNKNOWN;
        }
    }

    public String getOriginatingAddress() {
        return this.mWrappedSmsMessage.getOriginatingAddress();
    }

    public byte[] getPdu() {
        return this.mWrappedSmsMessage.getPdu();
    }

    public int getProtocolIdentifier() {
        return this.mWrappedSmsMessage.getProtocolIdentifier();
    }

    public String getPseudoSubject() {
        return this.mWrappedSmsMessage.getPseudoSubject();
    }

    public String getRecipientAddress() {
        return this.mWrappedSmsMessage.getRecipientAddress();
    }

    public String getServiceCenterAddress() {
        return this.mWrappedSmsMessage.getServiceCenterAddress();
    }

    public int getStatus() {
        return this.mWrappedSmsMessage.getStatus();
    }

    public int getStatusOnIcc() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    @Deprecated
    public int getStatusOnSim() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    public int getSubId() {
        return this.mSubId;
    }

    public long getTimestampMillis() {
        return this.mWrappedSmsMessage.getTimestampMillis();
    }

    public byte[] getUserData() {
        return this.mWrappedSmsMessage.getUserData();
    }

    public boolean isCphsMwiMessage() {
        return this.mWrappedSmsMessage.isCphsMwiMessage();
    }

    public boolean isEmail() {
        return this.mWrappedSmsMessage.isEmail();
    }

    public boolean isMWIClearMessage() {
        return this.mWrappedSmsMessage.isMWIClearMessage();
    }

    public boolean isMWISetMessage() {
        return this.mWrappedSmsMessage.isMWISetMessage();
    }

    public boolean isMwiDontStore() {
        return this.mWrappedSmsMessage.isMwiDontStore();
    }

    public boolean isReplace() {
        return this.mWrappedSmsMessage.isReplace();
    }

    public boolean isReplyPathPresent() {
        return this.mWrappedSmsMessage.isReplyPathPresent();
    }

    public boolean isStatusReportMessage() {
        return this.mWrappedSmsMessage.isStatusReportMessage();
    }

    public void setSubId(int i) {
        this.mSubId = i;
    }
}
