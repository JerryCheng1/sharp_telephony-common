package com.android.internal.telephony.imsphone;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import com.android.ims.ImsException;
import com.android.ims.ImsSsInfo;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccRecords;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImsPhoneMmiCode extends Handler implements MmiCode {
    private static final String ACTION_ACTIVATE = "*";
    private static final String ACTION_DEACTIVATE = "#";
    private static final String ACTION_ERASURE = "##";
    private static final String ACTION_INTERROGATE = "*#";
    private static final String ACTION_REGISTER = "**";
    private static final int CLIR_DEFAULT = 0;
    private static final int CLIR_INVOCATION = 1;
    private static final int CLIR_NOT_PROVISIONED = 0;
    private static final int CLIR_PRESENTATION_ALLOWED_TEMPORARY = 4;
    private static final int CLIR_PRESENTATION_RESTRICTED_TEMPORARY = 3;
    private static final int CLIR_PROVISIONED_PERMANENT = 1;
    private static final int CLIR_SUPPRESSION = 2;
    static final String CfuTimer = "Call Forward Unconditional Timer";
    private static final char END_OF_USSD_COMMAND = '#';
    private static final int EVENT_GET_CLIR_COMPLETE = 6;
    private static final int EVENT_QUERY_CFUT_COMPLETE = 8;
    private static final int EVENT_QUERY_CF_COMPLETE = 1;
    private static final int EVENT_QUERY_COMPLETE = 3;
    private static final int EVENT_QUERY_ICB_COMPLETE = 10;
    private static final int EVENT_SET_CFF_COMPLETE = 4;
    private static final int EVENT_SET_CFF_TIMER_COMPLETE = 9;
    private static final int EVENT_SET_COMPLETE = 0;
    private static final int EVENT_SUPP_SVC_QUERY_COMPLETE = 7;
    private static final int EVENT_USSD_CANCEL_COMPLETE = 5;
    private static final int EVENT_USSD_COMPLETE = 2;
    static final String IcbAnonymousMmi = "Anonymous Incoming Call Barring";
    static final String IcbDnMmi = "Specific Incoming Call Barring";
    static final String LOG_TAG = "ImsPhoneMmiCode";
    private static final int MATCH_GROUP_ACTION = 2;
    private static final int MATCH_GROUP_DIALING_NUMBER = 12;
    private static final int MATCH_GROUP_POUND_STRING = 1;
    private static final int MATCH_GROUP_PWD_CONFIRM = 11;
    private static final int MATCH_GROUP_SERVICE_CODE = 3;
    private static final int MATCH_GROUP_SIA = 5;
    private static final int MATCH_GROUP_SIB = 7;
    private static final int MATCH_GROUP_SIC = 9;
    private static final int MAX_LENGTH_SHORT_CODE = 2;
    private static final int NUM_PRESENTATION_ALLOWED = 0;
    private static final int NUM_PRESENTATION_RESTRICTED = 1;
    private static final String SC_BAIC = "35";
    public static final String SC_BAICa = "157";
    private static final String SC_BAICr = "351";
    private static final String SC_BAOC = "33";
    private static final String SC_BAOIC = "331";
    private static final String SC_BAOICxH = "332";
    private static final String SC_BA_ALL = "330";
    private static final String SC_BA_MO = "333";
    private static final String SC_BA_MT = "353";
    public static final String SC_BS_MT = "156";
    private static final String SC_CFB = "67";
    private static final String SC_CFNR = "62";
    private static final String SC_CFNRy = "61";
    private static final String SC_CFU = "21";
    private static final String SC_CFUT = "22";
    private static final String SC_CF_All = "002";
    private static final String SC_CF_All_Conditional = "004";
    private static final String SC_CLIP = "30";
    private static final String SC_CLIR = "31";
    private static final String SC_CNAP = "300";
    private static final String SC_COLP = "76";
    private static final String SC_COLR = "77";
    private static final String SC_PIN = "04";
    private static final String SC_PIN2 = "042";
    private static final String SC_PUK = "05";
    private static final String SC_PUK2 = "052";
    private static final String SC_PWD = "03";
    private static final String SC_WAIT = "43";
    public static final String UT_BUNDLE_KEY_CLIR = "queryClir";
    public static final String UT_BUNDLE_KEY_SSINFO = "imsSsInfo";
    private static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    private static String[] sTwoDigitNumberPattern;
    private String mAction;
    private Context mContext;
    private String mDialingNumber;
    private IccRecords mIccRecords;
    private boolean mIsCallFwdReg;
    private boolean mIsPendingUSSD;
    private boolean mIsUssdRequest;
    private CharSequence mMessage;
    private ImsPhone mPhone;
    private String mPoundString;
    private String mPwd;
    private String mSc;
    private String mSia;
    private String mSib;
    private String mSic;
    private State mState = State.PENDING;

    ImsPhoneMmiCode(ImsPhone imsPhone) {
        super(imsPhone.getHandler().getLooper());
        this.mPhone = imsPhone;
        this.mContext = imsPhone.getContext();
        this.mIccRecords = (IccRecords) this.mPhone.mDefaultPhone.mIccRecords.get();
    }

    private CharSequence createQueryCallWaitingResultMessage(int i) {
        StringBuilder stringBuilder = new StringBuilder(this.mContext.getText(17039549));
        for (int i2 = 1; i2 <= 128; i2 <<= 1) {
            if ((i2 & i) != 0) {
                stringBuilder.append("\n");
                stringBuilder.append(serviceClassToCFString(i2 & i));
            }
        }
        return stringBuilder;
    }

    private CharSequence getErrorMessage(AsyncResult asyncResult) {
        return this.mContext.getText(17039546);
    }

    private CharSequence getScString() {
        if (this.mSc != null) {
            if (isServiceCodeCallBarring(this.mSc)) {
                return this.mContext.getText(17039571);
            }
            if (isServiceCodeCallForwarding(this.mSc)) {
                return this.mContext.getText(17039569);
            }
            if (this.mSc.equals(SC_PWD)) {
                return this.mContext.getText(17039572);
            }
            if (this.mSc.equals(SC_WAIT)) {
                return this.mContext.getText(17039570);
            }
            if (this.mSc.equals(SC_CLIP)) {
                return this.mContext.getText(17039565);
            }
            if (this.mSc.equals(SC_CLIR)) {
                return this.mContext.getText(17039566);
            }
            if (this.mSc.equals(SC_COLP)) {
                return this.mContext.getText(17039567);
            }
            if (this.mSc.equals(SC_COLR)) {
                return this.mContext.getText(17039568);
            }
            if (this.mSc.equals(SC_CFUT)) {
                return CfuTimer;
            }
            if (this.mSc.equals(SC_BS_MT)) {
                return IcbDnMmi;
            }
            if (this.mSc.equals(SC_BAICa)) {
                return IcbAnonymousMmi;
            }
        }
        return "";
    }

    private static boolean isEmptyOrNull(CharSequence charSequence) {
        return charSequence == null || charSequence.length() == 0;
    }

    static boolean isScMatchesSuppServType(String str) {
        Matcher matcher = sPatternSuppService.matcher(str);
        if (matcher.matches()) {
            String makeEmptyNull = makeEmptyNull(matcher.group(3));
            if (makeEmptyNull.equals(SC_CFUT) || makeEmptyNull.equals(SC_BS_MT)) {
                return true;
            }
        }
        return false;
    }

    static boolean isServiceCodeCallBarring(String str) {
        Resources system = Resources.getSystem();
        if (str == null) {
            return false;
        }
        String[] stringArray = system.getStringArray(17236022);
        if (stringArray == null) {
            return false;
        }
        for (Object equals : stringArray) {
            if (str.equals(equals)) {
                return true;
            }
        }
        return false;
    }

    static boolean isServiceCodeCallForwarding(String str) {
        return str != null && (str.equals(SC_CFU) || str.equals(SC_CFB) || str.equals(SC_CFNRy) || str.equals(SC_CFNR) || str.equals(SC_CF_All) || str.equals(SC_CF_All_Conditional));
    }

    static boolean isServiceCodeUncondCallFwdTimer(String str) {
        return str != null && str.equals(SC_CFUT);
    }

    private static boolean isShortCode(String str, ImsPhone imsPhone) {
        return (str == null || str.length() == 0 || PhoneNumberUtils.isLocalEmergencyNumber(imsPhone.getContext(), str)) ? false : isShortCodeUSSD(str, imsPhone);
    }

    private static boolean isShortCodeUSSD(String str, ImsPhone imsPhone) {
        return (str == null || str.length() > 2) ? false : (!imsPhone.isInCall() && str.length() == 2 && str.charAt(0) == '1') ? false : true;
    }

    private static boolean isTwoDigitShortCode(Context context, String str) {
        Rlog.d(LOG_TAG, "isTwoDigitShortCode");
        if (str == null || str.length() > 2) {
            return false;
        }
        if (sTwoDigitNumberPattern == null) {
            sTwoDigitNumberPattern = context.getResources().getStringArray(17236010);
        }
        for (String str2 : sTwoDigitNumberPattern) {
            Rlog.d(LOG_TAG, "Two Digit Number Pattern " + str2);
            if (str.equals(str2)) {
                Rlog.d(LOG_TAG, "Two Digit Number Pattern -true");
                return true;
            }
        }
        Rlog.d(LOG_TAG, "Two Digit Number Pattern -false");
        return false;
    }

    private CharSequence makeCFQueryResultMessage(CallForwardInfo callForwardInfo, int i) {
        int i2 = callForwardInfo.reason == 2 ? 1 : 0;
        CharSequence text = callForwardInfo.status == 1 ? i2 != 0 ? this.mContext.getText(17039623) : this.mContext.getText(17039622) : (callForwardInfo.status == 0 && isEmptyOrNull(callForwardInfo.number)) ? this.mContext.getText(17039621) : i2 != 0 ? this.mContext.getText(17039625) : this.mContext.getText(17039624);
        CharSequence serviceClassToCFString = serviceClassToCFString(callForwardInfo.serviceClass & i);
        String stringFromStringAndTOA = PhoneNumberUtils.stringFromStringAndTOA(callForwardInfo.number, callForwardInfo.toa);
        String num = Integer.toString(callForwardInfo.timeSeconds);
        if (callForwardInfo.reason == 0 && (callForwardInfo.serviceClass & i) == 1) {
            boolean z = callForwardInfo.status == 1;
            if (this.mIccRecords != null) {
                this.mIccRecords.setVoiceCallForwardingFlag(1, z, callForwardInfo.number);
                Rlog.d(LOG_TAG, "makeCFQueryResultMessage cffEnabled  = " + z);
                this.mPhone.setCallForwardingPreference(z);
            }
        }
        return TextUtils.replace(text, new String[]{"{0}", "{1}", "{2}"}, new CharSequence[]{serviceClassToCFString, stringFromStringAndTOA, num});
    }

    private CharSequence makeCFTQueryResultMessage(CallForwardInfo callForwardInfo, int i) {
        Rlog.d(LOG_TAG, "makeCFTQueryResultMessage: ");
        CharSequence text = (callForwardInfo.status == 0 && isEmptyOrNull(callForwardInfo.number)) ? this.mContext.getText(17039621) : this.mContext.getText(17039624);
        CharSequence serviceClassToCFString = serviceClassToCFString(callForwardInfo.serviceClass & i);
        String stringFromStringAndTOA = PhoneNumberUtils.stringFromStringAndTOA(callForwardInfo.number, callForwardInfo.toa);
        String num = Integer.toString(callForwardInfo.timeSeconds);
        return TextUtils.replace(text, new String[]{"{0}", "{1}", "{2}"}, new CharSequence[]{serviceClassToCFString, stringFromStringAndTOA, num});
    }

    private static String makeEmptyNull(String str) {
        return (str == null || str.length() != 0) ? str : null;
    }

    static ImsPhoneMmiCode newFromDialString(String str, ImsPhone imsPhone) {
        Matcher matcher = sPatternSuppService.matcher(str);
        ImsPhoneMmiCode imsPhoneMmiCode;
        if (matcher.matches()) {
            imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
            imsPhoneMmiCode.mPoundString = makeEmptyNull(matcher.group(1));
            imsPhoneMmiCode.mAction = makeEmptyNull(matcher.group(2));
            imsPhoneMmiCode.mSc = makeEmptyNull(matcher.group(3));
            imsPhoneMmiCode.mSia = makeEmptyNull(matcher.group(5));
            imsPhoneMmiCode.mSib = makeEmptyNull(matcher.group(7));
            imsPhoneMmiCode.mSic = makeEmptyNull(matcher.group(9));
            imsPhoneMmiCode.mPwd = makeEmptyNull(matcher.group(11));
            imsPhoneMmiCode.mDialingNumber = makeEmptyNull(matcher.group(12));
            if (imsPhoneMmiCode.mDialingNumber == null || !imsPhoneMmiCode.mDialingNumber.endsWith(ACTION_DEACTIVATE) || !str.endsWith(ACTION_DEACTIVATE)) {
                return imsPhoneMmiCode;
            }
            imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
            imsPhoneMmiCode.mPoundString = str;
            return imsPhoneMmiCode;
        } else if (str.endsWith(ACTION_DEACTIVATE)) {
            imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
            imsPhoneMmiCode.mPoundString = str;
            return imsPhoneMmiCode;
        } else if (isTwoDigitShortCode(imsPhone.getContext(), str) || !isShortCode(str, imsPhone)) {
            return null;
        } else {
            imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
            imsPhoneMmiCode.mDialingNumber = str;
            return imsPhoneMmiCode;
        }
    }

    static ImsPhoneMmiCode newFromUssdUserInput(String str, ImsPhone imsPhone) {
        ImsPhoneMmiCode imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
        imsPhoneMmiCode.mMessage = str;
        imsPhoneMmiCode.mState = State.PENDING;
        imsPhoneMmiCode.mIsPendingUSSD = true;
        return imsPhoneMmiCode;
    }

    static ImsPhoneMmiCode newNetworkInitiatedUssd(String str, boolean z, ImsPhone imsPhone) {
        ImsPhoneMmiCode imsPhoneMmiCode = new ImsPhoneMmiCode(imsPhone);
        imsPhoneMmiCode.mMessage = str;
        imsPhoneMmiCode.mIsUssdRequest = z;
        if (z) {
            imsPhoneMmiCode.mIsPendingUSSD = true;
            imsPhoneMmiCode.mState = State.PENDING;
        } else {
            imsPhoneMmiCode.mState = State.COMPLETE;
        }
        return imsPhoneMmiCode;
    }

    private void onIcbQueryComplete(AsyncResult asyncResult) {
        Rlog.d(LOG_TAG, "onIcbQueryComplete ");
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof ImsException) {
                ImsException imsException = (ImsException) asyncResult.exception;
                if (imsException.getMessage() != null) {
                    stringBuilder.append(imsException.getMessage());
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
            } else {
                stringBuilder.append(getErrorMessage(asyncResult));
            }
        } else {
            ImsSsInfo[] imsSsInfoArr = (ImsSsInfo[]) asyncResult.result;
            if (imsSsInfoArr.length == 0) {
                stringBuilder.append(this.mContext.getText(17039550));
            } else {
                int length = imsSsInfoArr.length;
                for (int i = 0; i < length; i++) {
                    if (imsSsInfoArr[i].mIcbNum != null) {
                        stringBuilder.append("Num: " + imsSsInfoArr[i].mIcbNum + " status: " + imsSsInfoArr[i].mStatus + "\n");
                    } else if (imsSsInfoArr[i].mStatus == 1) {
                        stringBuilder.append(this.mContext.getText(17039548));
                    } else {
                        stringBuilder.append(this.mContext.getText(17039550));
                    }
                }
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryCfComplete(AsyncResult asyncResult) {
        int i = 1;
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof ImsException) {
                ImsException imsException = (ImsException) asyncResult.exception;
                if (imsException.getMessage() != null) {
                    stringBuilder.append(imsException.getMessage());
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
            } else {
                stringBuilder.append(getErrorMessage(asyncResult));
            }
        } else {
            CallForwardInfo[] callForwardInfoArr = (CallForwardInfo[]) asyncResult.result;
            if (callForwardInfoArr.length == 0) {
                stringBuilder.append(this.mContext.getText(17039550));
                if (this.mIccRecords != null) {
                    this.mPhone.setCallForwardingPreference(false);
                    this.mIccRecords.setVoiceCallForwardingFlag(1, false, null);
                }
            } else {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                while (true) {
                    int i2 = i;
                    if (i2 > 128) {
                        break;
                    }
                    int length = callForwardInfoArr.length;
                    for (i = 0; i < length; i++) {
                        if ((callForwardInfoArr[i].serviceClass & i2) != 0) {
                            spannableStringBuilder.append(makeCFQueryResultMessage(callForwardInfoArr[i], i2));
                            spannableStringBuilder.append("\n");
                        }
                    }
                    i = i2 << 1;
                }
                stringBuilder.append(spannableStringBuilder);
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryClirComplete(AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        this.mState = State.FAILED;
        if (asyncResult.exception == null) {
            int[] intArray = ((Bundle) asyncResult.result).getIntArray(UT_BUNDLE_KEY_CLIR);
            Rlog.d(LOG_TAG, "CLIR param n=" + intArray[0] + " m=" + intArray[1]);
            switch (intArray[1]) {
                case 0:
                    stringBuilder.append(this.mContext.getText(17039584));
                    this.mState = State.COMPLETE;
                    break;
                case 1:
                    stringBuilder.append(this.mContext.getText(17039585));
                    this.mState = State.COMPLETE;
                    break;
                case 3:
                    switch (intArray[0]) {
                        case 0:
                            stringBuilder.append(this.mContext.getText(17039580));
                            this.mState = State.COMPLETE;
                            break;
                        case 1:
                            stringBuilder.append(this.mContext.getText(17039580));
                            this.mState = State.COMPLETE;
                            break;
                        case 2:
                            stringBuilder.append(this.mContext.getText(17039581));
                            this.mState = State.COMPLETE;
                            break;
                        default:
                            stringBuilder.append(this.mContext.getText(17039546));
                            this.mState = State.FAILED;
                            break;
                    }
                case 4:
                    switch (intArray[0]) {
                        case 0:
                            stringBuilder.append(this.mContext.getText(17039583));
                            this.mState = State.COMPLETE;
                            break;
                        case 1:
                            stringBuilder.append(this.mContext.getText(17039582));
                            this.mState = State.COMPLETE;
                            break;
                        case 2:
                            stringBuilder.append(this.mContext.getText(17039583));
                            this.mState = State.COMPLETE;
                            break;
                        default:
                            stringBuilder.append(this.mContext.getText(17039546));
                            this.mState = State.FAILED;
                            break;
                    }
                default:
                    stringBuilder.append(this.mContext.getText(17039546));
                    this.mState = State.FAILED;
                    break;
            }
        } else if (asyncResult.exception instanceof ImsException) {
            ImsException imsException = (ImsException) asyncResult.exception;
            if (imsException.getMessage() != null) {
                stringBuilder.append(imsException.getMessage());
            } else {
                stringBuilder.append(getErrorMessage(asyncResult));
            }
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryComplete(AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof ImsException) {
                ImsException imsException = (ImsException) asyncResult.exception;
                if (imsException.getMessage() != null) {
                    stringBuilder.append(imsException.getMessage());
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
            } else {
                stringBuilder.append(getErrorMessage(asyncResult));
            }
        } else {
            int[] iArr = (int[]) asyncResult.result;
            if (iArr.length == 0) {
                stringBuilder.append(this.mContext.getText(17039546));
            } else if (iArr[0] == 0) {
                stringBuilder.append(this.mContext.getText(17039550));
            } else if (this.mSc.equals(SC_WAIT)) {
                stringBuilder.append(createQueryCallWaitingResultMessage(iArr[1]));
            } else if (iArr[0] == 1) {
                stringBuilder.append(this.mContext.getText(17039548));
            } else {
                stringBuilder.append(this.mContext.getText(17039546));
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryUncondCfTimerComplete(AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            stringBuilder.append(getErrorMessage(asyncResult));
        } else {
            CallForwardInfo[] callForwardInfoArr = (CallForwardInfo[]) asyncResult.result;
            if (callForwardInfoArr.length == 0) {
                Rlog.d(LOG_TAG, "In infos.length == 0");
                stringBuilder.append(this.mContext.getText(17039550));
            } else {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                for (int i = 1; i <= 128; i <<= 1) {
                    int i2 = 0;
                    int length = callForwardInfoArr.length;
                    while (i2 < length) {
                        if ((callForwardInfoArr[i2].serviceClass & i) == 1 && callForwardInfoArr[i2].status != 0) {
                            stringBuilder.append("StartTime: " + callForwardInfoArr[i2].startHour + ":" + callForwardInfoArr[i2].startMinute + "\n");
                            stringBuilder.append("EndTime: " + callForwardInfoArr[i2].endHour + ":" + callForwardInfoArr[i2].endMinute + "\n");
                            stringBuilder.append("Service:" + this.mContext.getText(17039599));
                        } else if ((callForwardInfoArr[i2].serviceClass & i) != 0) {
                            spannableStringBuilder.append(makeCFTQueryResultMessage(callForwardInfoArr[i2], i));
                            spannableStringBuilder.append("\n");
                        }
                        i2++;
                    }
                }
                stringBuilder.append(spannableStringBuilder);
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onSetComplete(Message message, AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (!(asyncResult.exception instanceof CommandException)) {
                ImsException imsException = (ImsException) asyncResult.exception;
                if (imsException.getMessage() != null) {
                    stringBuilder.append(imsException.getMessage());
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
            } else if (((CommandException) asyncResult.exception).getCommandError() == Error.PASSWORD_INCORRECT) {
                stringBuilder.append(this.mContext.getText(17039553));
            } else {
                stringBuilder.append(this.mContext.getText(17039546));
            }
        } else if (isActivate()) {
            this.mState = State.COMPLETE;
            if (this.mIsCallFwdReg) {
                stringBuilder.append(this.mContext.getText(17039551));
            } else {
                stringBuilder.append(this.mContext.getText(17039548));
            }
        } else if (isDeactivate()) {
            this.mState = State.COMPLETE;
            stringBuilder.append(this.mContext.getText(17039550));
        } else if (isRegister()) {
            this.mState = State.COMPLETE;
            stringBuilder.append(this.mContext.getText(17039551));
        } else if (isErasure()) {
            this.mState = State.COMPLETE;
            stringBuilder.append(this.mContext.getText(17039552));
        } else {
            this.mState = State.FAILED;
            stringBuilder.append(this.mContext.getText(17039546));
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onSuppSvcQueryComplete(AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof ImsException) {
                ImsException imsException = (ImsException) asyncResult.exception;
                if (imsException.getMessage() != null) {
                    stringBuilder.append(imsException.getMessage());
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
            } else {
                stringBuilder.append(getErrorMessage(asyncResult));
            }
        } else {
            this.mState = State.FAILED;
            if (asyncResult.result instanceof Bundle) {
                Rlog.d(LOG_TAG, "Received CLIP/COLP/COLR Response.");
                ImsSsInfo imsSsInfo = (ImsSsInfo) ((Bundle) asyncResult.result).getParcelable(UT_BUNDLE_KEY_SSINFO);
                if (imsSsInfo != null) {
                    Rlog.d(LOG_TAG, "ImsSsInfo mStatus = " + imsSsInfo.mStatus);
                    if (imsSsInfo.mStatus == 0) {
                        stringBuilder.append(this.mContext.getText(17039550));
                        this.mState = State.COMPLETE;
                    } else if (imsSsInfo.mStatus == 1) {
                        stringBuilder.append(this.mContext.getText(17039548));
                        this.mState = State.COMPLETE;
                    } else {
                        stringBuilder.append(this.mContext.getText(17039546));
                    }
                } else {
                    stringBuilder.append(this.mContext.getText(17039546));
                }
            } else {
                Rlog.d(LOG_TAG, "Received Call Barring Response.");
                if (((int[]) asyncResult.result)[0] == 1) {
                    stringBuilder.append(this.mContext.getText(17039548));
                    this.mState = State.COMPLETE;
                } else {
                    stringBuilder.append(this.mContext.getText(17039550));
                    this.mState = State.COMPLETE;
                }
            }
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void processIcbMmiCodeForUpdate() {
        String str = this.mSia;
        String[] strArr = null;
        if (str != null) {
            strArr = str.split("\\$");
        }
        try {
            this.mPhone.mCT.getUtInterface().updateCallBarring(10, callBarrAction(str), obtainMessage(0, this), strArr);
        } catch (ImsException e) {
            Rlog.d(LOG_TAG, "Could not get UT handle for updating ICB.");
        }
    }

    static String scToBarringFacility(String str) {
        if (str == null) {
            throw new RuntimeException("invalid call barring sc");
        } else if (str.equals(SC_BAOC)) {
            return CommandsInterface.CB_FACILITY_BAOC;
        } else {
            if (str.equals(SC_BAOIC)) {
                return CommandsInterface.CB_FACILITY_BAOIC;
            }
            if (str.equals(SC_BAOICxH)) {
                return CommandsInterface.CB_FACILITY_BAOICxH;
            }
            if (str.equals(SC_BAIC)) {
                return CommandsInterface.CB_FACILITY_BAIC;
            }
            if (str.equals(SC_BAICr)) {
                return CommandsInterface.CB_FACILITY_BAICr;
            }
            if (str.equals(SC_BA_ALL)) {
                return CommandsInterface.CB_FACILITY_BA_ALL;
            }
            if (str.equals(SC_BA_MO)) {
                return CommandsInterface.CB_FACILITY_BA_MO;
            }
            if (str.equals(SC_BA_MT)) {
                return CommandsInterface.CB_FACILITY_BA_MT;
            }
            throw new RuntimeException("invalid call barring sc");
        }
    }

    private static int scToCallForwardReason(String str) {
        if (str == null) {
            throw new RuntimeException("invalid call forward sc");
        } else if (str.equals(SC_CF_All)) {
            return 4;
        } else {
            if (str.equals(SC_CFU) || str.equals(SC_CFUT)) {
                return 0;
            }
            if (str.equals(SC_CFB)) {
                return 1;
            }
            if (str.equals(SC_CFNR)) {
                return 3;
            }
            if (str.equals(SC_CFNRy)) {
                return 2;
            }
            if (str.equals(SC_CF_All_Conditional)) {
                return 5;
            }
            throw new RuntimeException("invalid call forward sc");
        }
    }

    private CharSequence serviceClassToCFString(int i) {
        switch (i) {
            case 1:
                return this.mContext.getText(17039599);
            case 2:
                return this.mContext.getText(17039600);
            case 4:
                return this.mContext.getText(17039601);
            case 8:
                return this.mContext.getText(17039602);
            case 16:
                return this.mContext.getText(17039604);
            case 32:
                return this.mContext.getText(17039603);
            case 64:
                return this.mContext.getText(17039605);
            case 128:
                return this.mContext.getText(17039606);
            default:
                return null;
        }
    }

    private static int siToServiceClass(String str) {
        if (str == null || str.length() == 0) {
            return 0;
        }
        switch (Integer.parseInt(str, 10)) {
            case 10:
                return 13;
            case 11:
                return 1;
            case 12:
                return 12;
            case 13:
                return 4;
            case 16:
                return 8;
            case 19:
                return 5;
            case 20:
                return 48;
            case 21:
                return 160;
            case 22:
                return 80;
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                return 16;
            case 25:
                return 32;
            case 26:
                return 17;
            case 99:
                return 64;
            default:
                throw new RuntimeException("unsupported MMI service code " + str);
        }
    }

    private static int siToTime(String str) {
        return (str == null || str.length() == 0) ? 0 : Integer.parseInt(str, 10);
    }

    public int calForwardAction(String str) {
        if (isActivate()) {
            if (isEmptyOrNull(str)) {
                this.mIsCallFwdReg = false;
                return 1;
            }
            this.mIsCallFwdReg = true;
            return 3;
        } else if (isDeactivate()) {
            return 0;
        } else {
            if (isRegister()) {
                return 3;
            }
            if (isErasure()) {
                return 4;
            }
            throw new RuntimeException("invalid action");
        }
    }

    public int callBarrAction(String str) {
        if (isActivate()) {
            return 1;
        }
        if (isDeactivate()) {
            return 0;
        }
        if (isRegister()) {
            if (!isEmptyOrNull(str)) {
                return 3;
            }
            throw new RuntimeException("invalid action");
        } else if (isErasure()) {
            return 4;
        } else {
            throw new RuntimeException("invalid action");
        }
    }

    public void cancel() {
        if (this.mState != State.COMPLETE && this.mState != State.FAILED) {
            this.mState = State.CANCELLED;
            if (this.mIsPendingUSSD) {
                this.mPhone.cancelUSSD();
            } else {
                this.mPhone.onMMIDone(this);
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public int getCLIRMode() {
        if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
            if (isActivate()) {
                return 2;
            }
            if (isDeactivate()) {
                return 1;
            }
        }
        return 0;
    }

    public String getDialString() {
        return null;
    }

    /* Access modifiers changed, original: 0000 */
    public String getDialingNumber() {
        return this.mDialingNumber;
    }

    public CharSequence getMessage() {
        return this.mMessage;
    }

    public Phone getPhone() {
        return this.mPhone;
    }

    public State getState() {
        return this.mState;
    }

    public CharSequence getUssdCode() {
        return null;
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case 0:
                onSetComplete(message, (AsyncResult) message.obj);
                return;
            case 1:
                onQueryCfComplete((AsyncResult) message.obj);
                return;
            case 2:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null) {
                    this.mState = State.FAILED;
                    this.mMessage = getErrorMessage(asyncResult);
                    this.mPhone.onMMIDone(this);
                    return;
                }
                return;
            case 3:
                onQueryComplete((AsyncResult) message.obj);
                return;
            case 4:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null && message.arg1 == 1) {
                    boolean z = message.arg2 == 1;
                    if (this.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, z, this.mDialingNumber);
                        this.mPhone.setCallForwardingPreference(z);
                    }
                }
                onSetComplete(message, asyncResult);
                this.mPhone.updateCallForwardStatus();
                return;
            case 5:
                this.mPhone.onMMIDone(this);
                return;
            case 6:
                onQueryClirComplete((AsyncResult) message.obj);
                return;
            case 7:
                onSuppSvcQueryComplete((AsyncResult) message.obj);
                return;
            case 8:
                onQueryUncondCfTimerComplete((AsyncResult) message.obj);
                return;
            case 9:
                onSetComplete(message, (AsyncResult) message.obj);
                return;
            case 10:
                onIcbQueryComplete((AsyncResult) message.obj);
                return;
            default:
                return;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isActivate() {
        return this.mAction != null && this.mAction.equals("*");
    }

    public boolean isCancelable() {
        return this.mIsPendingUSSD;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isDeactivate() {
        return this.mAction != null && this.mAction.equals(ACTION_DEACTIVATE);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isErasure() {
        return this.mAction != null && this.mAction.equals(ACTION_ERASURE);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInterrogate() {
        return this.mAction != null && this.mAction.equals(ACTION_INTERROGATE);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isMMI() {
        return this.mPoundString != null;
    }

    public boolean isPendingUSSD() {
        return this.mIsPendingUSSD;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isShortCode() {
        return this.mPoundString == null && this.mDialingNumber != null && this.mDialingNumber.length() <= 2;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isSupportedOverImsPhone() {
        if (!TelBrand.IS_DCM) {
            if (isShortCode()) {
                return true;
            }
            if (this.mDialingNumber != null) {
                return false;
            }
            if (isServiceCodeCallForwarding(this.mSc) || isServiceCodeCallBarring(this.mSc) || ((this.mSc != null && this.mSc.equals(SC_WAIT)) || ((this.mSc != null && this.mSc.equals(SC_CLIR)) || ((this.mSc != null && this.mSc.equals(SC_CLIP)) || ((this.mSc != null && this.mSc.equals(SC_COLR)) || ((this.mSc != null && this.mSc.equals(SC_COLP)) || ((this.mSc != null && this.mSc.equals(SC_BS_MT)) || (this.mSc != null && this.mSc.equals(SC_BAICa))))))))) {
                int siToServiceClass = siToServiceClass(this.mSib);
                return siToServiceClass == 0 || siToServiceClass == 1;
            } else if (isPinPukCommand() || (this.mSc != null && (this.mSc.equals(SC_PWD) || this.mSc.equals(SC_CLIP) || this.mSc.equals(SC_CLIR)))) {
                return false;
            } else {
                if (this.mPoundString != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isTemporaryModeCLIR() {
        return this.mSc != null && this.mSc.equals(SC_CLIR) && this.mDialingNumber != null && (isActivate() || isDeactivate());
    }

    public boolean isUssdRequest() {
        return this.mIsUssdRequest;
    }

    /* Access modifiers changed, original: 0000 */
    public void onUssdFinished(String str, boolean z) {
        if (this.mState == State.PENDING) {
            if (str == null) {
                this.mMessage = this.mContext.getText(17039554);
            } else {
                this.mMessage = str;
            }
            this.mIsUssdRequest = z;
            if (!z) {
                this.mState = State.COMPLETE;
            }
            this.mPhone.onMMIDone(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onUssdFinishedError() {
        if (this.mState == State.PENDING) {
            this.mState = State.FAILED;
            this.mMessage = this.mContext.getText(17039546);
            this.mPhone.onMMIDone(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void processCode() throws CallStateException {
        int i = 0;
        int scToCallForwardReason;
        int siToServiceClass;
        int siToTime;
        int calForwardAction;
        int i2;
        int parseInt;
        if (isShortCode()) {
            Rlog.d(LOG_TAG, "isShortCode");
            Rlog.d(LOG_TAG, "Sending short code '" + this.mDialingNumber + "' over CS pipe.");
            throw new CallStateException(ImsPhone.CS_FALLBACK);
        } else if (isServiceCodeCallForwarding(this.mSc)) {
            Rlog.d(LOG_TAG, "is CF");
            String str = this.mSia;
            scToCallForwardReason = scToCallForwardReason(this.mSc);
            siToServiceClass = siToServiceClass(this.mSib);
            siToTime = siToTime(this.mSic);
            if (isInterrogate()) {
                this.mPhone.getCallForwardingOption(scToCallForwardReason, obtainMessage(1, this));
                return;
            }
            calForwardAction = calForwardAction(str);
            i2 = (scToCallForwardReason == 0 || scToCallForwardReason == 4) ? 1 : 0;
            if (calForwardAction == 1 || calForwardAction == 3) {
                i = 1;
            }
            Rlog.d(LOG_TAG, "is CF setCallForward");
            this.mPhone.setCallForwardingOption(calForwardAction, scToCallForwardReason, str, siToServiceClass, siToTime, obtainMessage(4, i2, i, this));
        } else if (isServiceCodeUncondCallFwdTimer(this.mSc)) {
            Rlog.d(LOG_TAG, "is UncondCFTimer");
            String str2 = this.mSia;
            i2 = scToCallForwardReason(this.mSc);
            if (isInterrogate()) {
                this.mPhone.getCallForwardingOption(i2, obtainMessage(8, this));
                return;
            }
            int parseInt2;
            if (this.mSic != null) {
                String[] split = this.mSic.split("\\$");
                if (!(split[0] == null || split[1] == null)) {
                    String[] split2 = split[0].split("\\:");
                    parseInt = split2[0] != null ? Integer.parseInt(split2[0]) : 0;
                    scToCallForwardReason = split2[1] != null ? Integer.parseInt(split2[1]) : 0;
                    String[] split3 = split[1].split("\\:");
                    parseInt2 = split3[0] != null ? Integer.parseInt(split3[0]) : 0;
                    if (split3[1] != null) {
                        siToServiceClass = Integer.parseInt(split3[1]);
                        calForwardAction = parseInt;
                    } else {
                        siToServiceClass = 0;
                        calForwardAction = parseInt;
                    }
                    siToTime = calForwardAction(str2);
                    Rlog.d(LOG_TAG, "is CFUT setCallForward");
                    this.mPhone.setCallForwardingUncondTimerOption(calForwardAction, scToCallForwardReason, parseInt2, siToServiceClass, siToTime, i2, str2, obtainMessage(9, this));
                }
            }
            siToServiceClass = 0;
            parseInt2 = 0;
            scToCallForwardReason = 0;
            calForwardAction = 0;
            siToTime = calForwardAction(str2);
            Rlog.d(LOG_TAG, "is CFUT setCallForward");
            this.mPhone.setCallForwardingUncondTimerOption(calForwardAction, scToCallForwardReason, parseInt2, siToServiceClass, siToTime, i2, str2, obtainMessage(9, this));
        } else if (isServiceCodeCallBarring(this.mSc)) {
            String str3 = this.mSia;
            String scToBarringFacility = scToBarringFacility(this.mSc);
            if (isInterrogate()) {
                this.mPhone.getCallBarring(scToBarringFacility, obtainMessage(7, this));
            } else if (isActivate() || isDeactivate()) {
                this.mPhone.setCallBarring(scToBarringFacility, isActivate(), str3, obtainMessage(0, this));
            } else {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
        } else if (this.mSc == null || !this.mSc.equals(SC_CLIR)) {
            if (this.mSc == null || !this.mSc.equals(SC_CLIP)) {
                if (this.mSc == null || !this.mSc.equals(SC_COLP)) {
                    if (this.mSc == null || !this.mSc.equals(SC_COLR)) {
                        if (this.mSc == null || !this.mSc.equals(SC_BS_MT)) {
                            try {
                                if (this.mSc != null && this.mSc.equals(SC_BAICa)) {
                                    try {
                                        if (isInterrogate()) {
                                            this.mPhone.mCT.getUtInterface().queryCallBarring(6, obtainMessage(10, this));
                                            return;
                                        }
                                        if (isActivate()) {
                                            i = 1;
                                        } else if (isDeactivate()) {
                                        }
                                        this.mPhone.mCT.getUtInterface().updateCallBarring(6, i, obtainMessage(0, this), null);
                                        return;
                                    } catch (ImsException e) {
                                        Rlog.d(LOG_TAG, "Could not get UT handle for ICBa.");
                                        return;
                                    }
                                } else if (this.mSc != null && this.mSc.equals(SC_WAIT)) {
                                    parseInt = siToServiceClass(this.mSib);
                                    if (isActivate() || isDeactivate()) {
                                        this.mPhone.setCallWaiting(isActivate(), parseInt, obtainMessage(0, this));
                                        return;
                                    } else if (isInterrogate()) {
                                        this.mPhone.getCallWaiting(obtainMessage(3, this));
                                        return;
                                    } else {
                                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                                    }
                                } else if (this.mPoundString != null) {
                                    Rlog.d(LOG_TAG, "Sending pound string '" + this.mDialingNumber + "' over CS pipe.");
                                    throw new CallStateException(ImsPhone.CS_FALLBACK);
                                } else {
                                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                                }
                            } catch (RuntimeException e2) {
                                this.mState = State.FAILED;
                                this.mMessage = this.mContext.getText(17039546);
                                this.mPhone.onMMIDone(this);
                                return;
                            }
                        }
                        try {
                            if (isInterrogate()) {
                                this.mPhone.mCT.getUtInterface().queryCallBarring(10, obtainMessage(10, this));
                            } else {
                                processIcbMmiCodeForUpdate();
                            }
                        } catch (ImsException e3) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for ICB.");
                        }
                    } else if (isActivate()) {
                        try {
                            this.mPhone.mCT.getUtInterface().updateCOLR(1, obtainMessage(0, this));
                        } catch (ImsException e4) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLR.");
                        }
                    } else if (isDeactivate()) {
                        try {
                            this.mPhone.mCT.getUtInterface().updateCOLR(0, obtainMessage(0, this));
                        } catch (ImsException e5) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLR.");
                        }
                    } else if (isInterrogate()) {
                        try {
                            this.mPhone.mCT.getUtInterface().queryCOLR(obtainMessage(7, this));
                        } catch (ImsException e6) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for queryCOLR.");
                        }
                    } else {
                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                    }
                } else if (isInterrogate()) {
                    try {
                        this.mPhone.mCT.getUtInterface().queryCOLP(obtainMessage(7, this));
                    } catch (ImsException e7) {
                        Rlog.d(LOG_TAG, "Could not get UT handle for queryCOLP.");
                    }
                } else if (isActivate() || isDeactivate()) {
                    try {
                        this.mPhone.mCT.getUtInterface().updateCOLP(isActivate(), obtainMessage(0, this));
                    } catch (ImsException e8) {
                        Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLP.");
                    }
                } else {
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            } else if (isInterrogate()) {
                try {
                    this.mPhone.mCT.getUtInterface().queryCLIP(obtainMessage(7, this));
                } catch (ImsException e9) {
                    Rlog.d(LOG_TAG, "Could not get UT handle for queryCLIP.");
                }
            } else if (isActivate() || isDeactivate()) {
                try {
                    this.mPhone.mCT.getUtInterface().updateCLIP(isActivate(), obtainMessage(0, this));
                } catch (ImsException e10) {
                    Rlog.d(LOG_TAG, "Could not get UT handle for updateCLIP.");
                }
            } else {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
        } else if (isInterrogate()) {
            try {
                this.mPhone.mCT.getUtInterface().queryCLIR(obtainMessage(6, this));
            } catch (ImsException e11) {
                Rlog.d(LOG_TAG, "Could not get UT handle for queryCLIR.");
            }
        } else {
            throw new RuntimeException("Invalid or Unsupported MMI Code");
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendUssd(String str) {
        this.mIsPendingUSSD = true;
        this.mPhone.sendUSSD(str, obtainMessage(2, this));
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("ImsPhoneMmiCode {");
        stringBuilder.append("State=" + getState());
        if (this.mAction != null) {
            stringBuilder.append(" action=" + this.mAction);
        }
        if (this.mSc != null) {
            stringBuilder.append(" sc=" + this.mSc);
        }
        if (this.mSia != null) {
            stringBuilder.append(" sia=" + this.mSia);
        }
        if (this.mSib != null) {
            stringBuilder.append(" sib=" + this.mSib);
        }
        if (this.mSic != null) {
            stringBuilder.append(" sic=" + this.mSic);
        }
        if (this.mPoundString != null) {
            stringBuilder.append(" poundString=" + this.mPoundString);
        }
        if (this.mDialingNumber != null) {
            stringBuilder.append(" dialingNumber=" + this.mDialingNumber);
        }
        if (this.mPwd != null) {
            stringBuilder.append(" pwd=" + this.mPwd);
        }
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
