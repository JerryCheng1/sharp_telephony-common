package com.android.internal.telephony.gsm;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.BidiFormatter;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.gsm.SsData.RequestType;
import com.android.internal.telephony.gsm.SsData.ServiceType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GsmMmiCode extends Handler implements MmiCode {
    static final String ACTION_ACTIVATE = "*";
    static final String ACTION_DEACTIVATE = "#";
    static final String ACTION_ERASURE = "##";
    static final String ACTION_INTERROGATE = "*#";
    static final String ACTION_REGISTER = "**";
    static final char END_OF_USSD_COMMAND = '#';
    static final int EVENT_GET_CLIR_COMPLETE = 2;
    static final int EVENT_QUERY_CF_COMPLETE = 3;
    static final int EVENT_QUERY_COMPLETE = 5;
    static final int EVENT_SET_CFF_COMPLETE = 6;
    static final int EVENT_SET_COMPLETE = 1;
    static final int EVENT_USSD_CANCEL_COMPLETE = 7;
    static final int EVENT_USSD_COMPLETE = 4;
    static final String LOG_TAG = "GsmMmiCode";
    static final int MATCH_GROUP_ACTION = 2;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final int MAX_LENGTH_SHORT_CODE = 2;
    static final String SC_BAIC = "35";
    static final String SC_BAICr = "351";
    static final String SC_BAOC = "33";
    static final String SC_BAOIC = "331";
    static final String SC_BAOICxH = "332";
    static final String SC_BA_ALL = "330";
    static final String SC_BA_MO = "333";
    static final String SC_BA_MT = "353";
    static final String SC_CFB = "67";
    static final String SC_CFNR = "62";
    static final String SC_CFNRy = "61";
    static final String SC_CFU = "21";
    static final String SC_CF_All = "002";
    static final String SC_CF_All_Conditional = "004";
    static final String SC_CLIP = "30";
    static final String SC_CLIR = "31";
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static final String SC_PWD = "03";
    static final String SC_WAIT = "43";
    static Pattern sPatternSuppService = (TelBrand.IS_DCM ? Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(((.*)([^#]+))?)") : Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)"));
    private static String[] sTwoDigitNumberPattern;
    String mAction;
    Context mContext;
    private String mDialString = null;
    String mDialingNumber;
    IccRecords mIccRecords;
    private boolean mIsCallFwdReg;
    private boolean mIsPendingUSSD;
    private boolean mIsSsInfo = false;
    private boolean mIsUssdRequest;
    CharSequence mMessage;
    GSMPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    State mState = State.PENDING;
    UiccCardApplication mUiccApplication;
    private CharSequence mUssdCode = null;

    GsmMmiCode(GSMPhone gSMPhone, UiccCardApplication uiccCardApplication) {
        super(gSMPhone.getHandler().getLooper());
        this.mPhone = gSMPhone;
        this.mContext = gSMPhone.getContext();
        this.mUiccApplication = uiccCardApplication;
        if (uiccCardApplication != null) {
            this.mIccRecords = uiccCardApplication.getIccRecords();
        }
    }

    private CharSequence createQueryCallBarringResultMessage(int i) {
        StringBuilder stringBuilder = new StringBuilder(this.mContext.getText(17039549));
        for (int i2 = 1; i2 <= 128; i2 <<= 1) {
            if ((i2 & i) != 0) {
                stringBuilder.append("\n");
                stringBuilder.append(serviceClassToCFString(i2 & i));
            }
        }
        return stringBuilder;
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

    private String formatLtr(String str) {
        return str == null ? str : BidiFormatter.getInstance().unicodeWrap(str, TextDirectionHeuristics.LTR, true);
    }

    private String getActionStringFromReqType(RequestType requestType) {
        switch (requestType) {
            case SS_ACTIVATION:
                return "*";
            case SS_DEACTIVATION:
                return ACTION_DEACTIVATE;
            case SS_REGISTRATION:
                return ACTION_REGISTER;
            case SS_ERASURE:
                return ACTION_ERASURE;
            case SS_INTERROGATION:
                return ACTION_INTERROGATE;
            default:
                return "";
        }
    }

    private CharSequence getErrorCode(AsyncResult asyncResult) {
        String str = "";
        if (!TelBrand.IS_DCM || !(asyncResult.exception instanceof CommandException)) {
            return str;
        }
        switch (((CommandException) asyncResult.exception).getCommandError()) {
            case OP_NOT_ALLOWED_BEFORE_REG_NW:
            case REQUEST_NOT_SUPPORTED:
            case SS_ERROR_STATUS:
                return "17";
            case PASSWORD_INCORRECT:
                return "38";
            case RADIO_NOT_AVAILABLE:
                return "13";
            case GENERIC_FAILURE:
                return "6";
            case INVALID_RESPONSE:
                return "5";
            case OP_NOT_ALLOWED_DURING_VOICE_CALL:
                return "4";
            case SIM_PIN2:
                return "3";
            case SIM_PUK2:
                return "2";
            case SMS_FAIL_RETRY:
                return "1";
            default:
                return "0";
        }
    }

    private CharSequence getErrorMessage(AsyncResult asyncResult) {
        if (asyncResult.exception instanceof CommandException) {
            Error commandError = ((CommandException) asyncResult.exception).getCommandError();
            if (commandError == Error.FDN_CHECK_FAILURE) {
                Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                return this.mContext.getText(17039547);
            } else if (commandError == Error.USSD_MODIFIED_TO_DIAL) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_DIAL");
                return this.mContext.getText(17041178);
            } else if (commandError == Error.USSD_MODIFIED_TO_SS) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_SS");
                return this.mContext.getText(17041179);
            } else if (commandError == Error.USSD_MODIFIED_TO_USSD) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_USSD");
                return this.mContext.getText(17041180);
            } else if (commandError == Error.SS_MODIFIED_TO_DIAL) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_DIAL");
                return this.mContext.getText(17041181);
            } else if (commandError == Error.SS_MODIFIED_TO_USSD) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_USSD");
                return this.mContext.getText(17041182);
            } else if (commandError == Error.SS_MODIFIED_TO_SS) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_SS");
                return this.mContext.getText(17041183);
            }
        }
        return this.mContext.getText(17039546);
    }

    private int getIccPinPukRetryCountSc(int i) {
        try {
            return Stub.asInterface(ServiceManager.checkService("phone")).getIccPinPukRetryCountSc(i);
        } catch (RemoteException e) {
            return -1;
        }
    }

    private CharSequence getScString() {
        if (this.mSc != null) {
            if (isServiceCodeCallBarring(this.mSc)) {
                return this.mContext.getText(17039571);
            }
            if (isServiceCodeCallForwarding(this.mSc)) {
                return this.mContext.getText(17039569);
            }
            if (this.mSc.equals(SC_CLIP)) {
                return this.mContext.getText(17039565);
            }
            if (this.mSc.equals(SC_CLIR)) {
                return this.mContext.getText(17039566);
            }
            if (this.mSc.equals(SC_PWD)) {
                return this.mContext.getText(17039572);
            }
            if (this.mSc.equals(SC_WAIT)) {
                return this.mContext.getText(17039570);
            }
            if (isPinPukCommand()) {
                return TelBrand.IS_DCM ? (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) ? this.mContext.getText(17041206) : this.mSc.equals(SC_PIN2) ? this.mContext.getText(17041207) : this.mContext.getText(17039573) : this.mContext.getText(17039573);
            }
        }
        return "";
    }

    private String getScStringFromScType(ServiceType serviceType) {
        switch (serviceType) {
            case SS_CFU:
                return SC_CFU;
            case SS_CF_BUSY:
                return SC_CFB;
            case SS_CF_NO_REPLY:
                return SC_CFNRy;
            case SS_CF_NOT_REACHABLE:
                return SC_CFNR;
            case SS_CF_ALL:
                return SC_CF_All;
            case SS_CF_ALL_CONDITIONAL:
                return SC_CF_All_Conditional;
            case SS_CLIP:
                return SC_CLIP;
            case SS_CLIR:
                return SC_CLIR;
            case SS_WAIT:
                return SC_WAIT;
            case SS_BAOC:
                return SC_BAOC;
            case SS_BAOIC:
                return SC_BAOIC;
            case SS_BAOIC_EXC_HOME:
                return SC_BAOICxH;
            case SS_BAIC:
                return SC_BAIC;
            case SS_BAIC_ROAMING:
                return SC_BAICr;
            case SS_ALL_BARRING:
                return SC_BA_ALL;
            case SS_OUTGOING_BARRING:
                return SC_BA_MO;
            case SS_INCOMING_BARRING:
                return SC_BA_MT;
            default:
                return "";
        }
    }

    private void handlePasswordError(int i) {
        this.mState = State.FAILED;
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        stringBuilder.append(this.mContext.getText(i));
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private static boolean isEmptyOrNull(CharSequence charSequence) {
        return charSequence == null || charSequence.length() == 0;
    }

    private boolean isServiceClassVoiceorNone(int i) {
        return (i & 1) != 0 || i == 0;
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

    private static boolean isShortCode(String str, GSMPhone gSMPhone) {
        return (str == null || str.length() == 0 || PhoneNumberUtils.isLocalEmergencyNumber(gSMPhone.getContext(), str)) ? false : isShortCodeUSSD(str, gSMPhone);
    }

    private static boolean isShortCodeUSSD(String str, GSMPhone gSMPhone) {
        return (str == null || str.length() > 2) ? false : (!gSMPhone.isInCall() && str.length() == 2 && str.charAt(0) == '1') ? false : true;
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
        String formatLtr = formatLtr(PhoneNumberUtils.stringFromStringAndTOA(callForwardInfo.number, callForwardInfo.toa));
        String num = Integer.toString(callForwardInfo.timeSeconds);
        if (callForwardInfo.reason == 0 && (callForwardInfo.serviceClass & i) == 1) {
            boolean z = callForwardInfo.status == 1;
            if (this.mIccRecords != null) {
                this.mIccRecords.setVoiceCallForwardingFlag(1, z, callForwardInfo.number);
                this.mPhone.setCallForwardingPreference(z);
            }
        }
        return TextUtils.replace(text, new String[]{"{0}", "{1}", "{2}"}, new CharSequence[]{serviceClassToCFString, formatLtr, num});
    }

    private static String makeEmptyNull(String str) {
        return (str == null || str.length() != 0) ? str : null;
    }

    static GsmMmiCode newFromDialString(String str, GSMPhone gSMPhone, UiccCardApplication uiccCardApplication) {
        Matcher matcher = sPatternSuppService.matcher(str);
        GsmMmiCode gsmMmiCode;
        if (matcher.matches()) {
            gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
            gsmMmiCode.mPoundString = makeEmptyNull(matcher.group(1));
            gsmMmiCode.mAction = makeEmptyNull(matcher.group(2));
            gsmMmiCode.mSc = makeEmptyNull(matcher.group(3));
            gsmMmiCode.mSia = makeEmptyNull(matcher.group(5));
            gsmMmiCode.mSib = makeEmptyNull(matcher.group(7));
            gsmMmiCode.mSic = makeEmptyNull(matcher.group(9));
            gsmMmiCode.mPwd = makeEmptyNull(matcher.group(11));
            gsmMmiCode.mDialingNumber = makeEmptyNull(matcher.group(12));
            if (TelBrand.IS_DCM) {
                gsmMmiCode.mDialString = str;
            }
            if (gsmMmiCode.mDialingNumber == null || !gsmMmiCode.mDialingNumber.endsWith(ACTION_DEACTIVATE) || !str.endsWith(ACTION_DEACTIVATE)) {
                return gsmMmiCode;
            }
            gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
            gsmMmiCode.mPoundString = str;
            return gsmMmiCode;
        } else if (str.endsWith(ACTION_DEACTIVATE)) {
            gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
            gsmMmiCode.mPoundString = str;
            if (!TelBrand.IS_DCM) {
                return gsmMmiCode;
            }
            gsmMmiCode.mDialString = str;
            return gsmMmiCode;
        } else if (isTwoDigitShortCode(gSMPhone.getContext(), str) || !isShortCode(str, gSMPhone)) {
            return null;
        } else {
            gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
            gsmMmiCode.mDialingNumber = str;
            if (!TelBrand.IS_DCM) {
                return gsmMmiCode;
            }
            gsmMmiCode.mDialString = str;
            return gsmMmiCode;
        }
    }

    static GsmMmiCode newFromUssdUserInput(String str, GSMPhone gSMPhone, UiccCardApplication uiccCardApplication) {
        GsmMmiCode gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
        gsmMmiCode.mMessage = str;
        gsmMmiCode.mState = State.PENDING;
        gsmMmiCode.mIsPendingUSSD = true;
        return gsmMmiCode;
    }

    static GsmMmiCode newNetworkInitiatedUssd(String str, boolean z, GSMPhone gSMPhone, UiccCardApplication uiccCardApplication) {
        GsmMmiCode gsmMmiCode = new GsmMmiCode(gSMPhone, uiccCardApplication);
        gsmMmiCode.mMessage = str;
        gsmMmiCode.mIsUssdRequest = z;
        if (z) {
            gsmMmiCode.mIsPendingUSSD = true;
            gsmMmiCode.mState = State.PENDING;
        } else {
            gsmMmiCode.mState = State.COMPLETE;
        }
        return gsmMmiCode;
    }

    private void onGetClirComplete(AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception == null) {
            int[] iArr = (int[]) asyncResult.result;
            switch (iArr[1]) {
                case 0:
                    stringBuilder.append(this.mContext.getText(17039584));
                    this.mState = State.COMPLETE;
                    if (TelBrand.IS_DCM) {
                        this.mUssdCode = null;
                        break;
                    }
                    break;
                case 1:
                    stringBuilder.append(this.mContext.getText(17039585));
                    this.mState = State.COMPLETE;
                    if (TelBrand.IS_DCM) {
                        this.mUssdCode = null;
                        break;
                    }
                    break;
                case 2:
                    stringBuilder.append(this.mContext.getText(17039546));
                    this.mState = State.FAILED;
                    if (TelBrand.IS_DCM) {
                        this.mUssdCode = null;
                        break;
                    }
                    break;
                case 3:
                    switch (iArr[0]) {
                        case 1:
                            stringBuilder.append(this.mContext.getText(17039580));
                            break;
                        case 2:
                            stringBuilder.append(this.mContext.getText(17039581));
                            break;
                        default:
                            stringBuilder.append(this.mContext.getText(17039580));
                            break;
                    }
                    this.mState = State.COMPLETE;
                    if (TelBrand.IS_DCM) {
                        this.mUssdCode = "31*5****1#";
                        break;
                    }
                    break;
                case 4:
                    switch (iArr[0]) {
                        case 1:
                            stringBuilder.append(this.mContext.getText(17039582));
                            break;
                        case 2:
                            stringBuilder.append(this.mContext.getText(17039583));
                            break;
                        default:
                            stringBuilder.append(this.mContext.getText(17039583));
                            break;
                    }
                    this.mState = State.COMPLETE;
                    if (TelBrand.IS_DCM) {
                        this.mUssdCode = "31*5****2#";
                        break;
                    }
                    break;
            }
        }
        this.mState = State.FAILED;
        stringBuilder.append(getErrorMessage(asyncResult));
        if (TelBrand.IS_DCM) {
            this.mUssdCode = this.mSc + "*" + getErrorCode(asyncResult) + ACTION_DEACTIVATE;
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
            stringBuilder.append(getErrorMessage(asyncResult));
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

    private void onQueryComplete(AsyncResult asyncResult) {
        int i = 0;
        StringBuilder stringBuilder = new StringBuilder(getScString());
        if (!TelBrand.IS_DCM) {
            stringBuilder.append("\n");
        } else if (SC_WAIT.equals(this.mSc)) {
            stringBuilder.append(this.mContext.getText(17041220));
        } else {
            stringBuilder.append("\n");
        }
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            stringBuilder.append(getErrorMessage(asyncResult));
            if (TelBrand.IS_DCM) {
                this.mUssdCode = this.mSc + "*" + getErrorCode(asyncResult) + ACTION_DEACTIVATE;
            }
        } else {
            int[] iArr = (int[]) asyncResult.result;
            if (iArr.length != 0) {
                if (TelBrand.IS_DCM) {
                    this.mUssdCode = null;
                }
                if (iArr[0] == 0) {
                    if (TelBrand.IS_DCM && this.mSc.equals(SC_WAIT)) {
                        if (iArr[1] != 0) {
                            this.mUssdCode = "43*0#";
                        } else {
                            this.mUssdCode = "43*4#";
                        }
                    } else if (TelBrand.IS_DCM && this.mSc.equals(SC_BAICr)) {
                        this.mUssdCode = "351*4#";
                    }
                    if (TelBrand.IS_DCM) {
                        stringBuilder.append(this.mContext.getText(17041218));
                    } else {
                        stringBuilder.append(this.mContext.getText(17039550));
                    }
                } else if (!TelBrand.IS_DCM && this.mSc.equals(SC_WAIT)) {
                    stringBuilder.append(createQueryCallWaitingResultMessage(iArr[1]));
                } else if (isServiceCodeCallBarring(this.mSc)) {
                    stringBuilder.append(createQueryCallBarringResultMessage(iArr[0]));
                    if (TelBrand.IS_DCM) {
                        if ((iArr[0] & 50) == 0) {
                            this.mUssdCode = this.mSc + "**24*16*32#";
                        } else if ((iArr[0] & 205) == 0) {
                            this.mUssdCode = this.mSc + "**24#";
                        } else {
                            this.mUssdCode = this.mSc + "**24*16*32#";
                        }
                    }
                } else if (iArr[0] == 1) {
                    stringBuilder.append(this.mContext.getText(17039548));
                    if (TelBrand.IS_DCM && this.mSc.equals(SC_WAIT)) {
                        this.mUssdCode = "43*5#";
                    }
                } else {
                    stringBuilder.append(this.mContext.getText(17039546));
                }
                if (TelBrand.IS_DCM && this.mUssdCode == null) {
                    this.mUssdCode = this.mSc + "*";
                    int length = iArr.length;
                    while (i < length) {
                        this.mUssdCode += "*" + Integer.toString(iArr[i]);
                        i++;
                    }
                    this.mUssdCode += ACTION_DEACTIVATE;
                }
            } else {
                stringBuilder.append(this.mContext.getText(17039546));
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private void onSetComplete(Message message, AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        if (!TelBrand.IS_DCM) {
            stringBuilder.append("\n");
        } else if (!SC_WAIT.equals(this.mSc)) {
            stringBuilder.append("\n");
        } else if (asyncResult.exception == null) {
            stringBuilder.append(this.mContext.getText(17041219));
        } else {
            stringBuilder.append(this.mContext.getText(17041220));
        }
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof CommandException) {
                Error commandError = ((CommandException) asyncResult.exception).getCommandError();
                if (commandError == Error.PASSWORD_INCORRECT) {
                    if (isPinPukCommand()) {
                        int iccPinPukRetryCountSc;
                        if (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) {
                            stringBuilder.append(this.mContext.getText(17039556));
                            if (TelBrand.IS_DCM) {
                                iccPinPukRetryCountSc = this.mSc.equals(SC_PUK2) ? getIccPinPukRetryCountSc(4) : getIccPinPukRetryCountSc(2);
                                if (iccPinPukRetryCountSc > 0) {
                                    stringBuilder.append(this.mContext.getText(17041212));
                                    stringBuilder.append(iccPinPukRetryCountSc);
                                    stringBuilder.append(this.mContext.getText(17041213));
                                }
                            }
                        } else if (TelBrand.IS_DCM) {
                            if (this.mSc.equals(SC_PIN2)) {
                                stringBuilder.append(this.mContext.getText(17041208));
                            } else {
                                stringBuilder.append(this.mContext.getText(17039555));
                            }
                            iccPinPukRetryCountSc = this.mSc.equals(SC_PIN2) ? getIccPinPukRetryCountSc(3) : getIccPinPukRetryCountSc(1);
                            if (iccPinPukRetryCountSc > 0) {
                                stringBuilder.append(this.mContext.getText(17041212));
                                stringBuilder.append(iccPinPukRetryCountSc);
                                stringBuilder.append(this.mContext.getText(17041213));
                            } else if (iccPinPukRetryCountSc == 0) {
                                stringBuilder.append(this.mContext.getText(17039560));
                            }
                        } else {
                            stringBuilder.append(this.mContext.getText(17039555));
                        }
                        iccPinPukRetryCountSc = message.arg1;
                        if (iccPinPukRetryCountSc <= 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: PUK locked, cancel as lock screen will handle this");
                            this.mState = State.CANCELLED;
                        } else if (iccPinPukRetryCountSc > 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: attemptsRemaining=" + iccPinPukRetryCountSc);
                            stringBuilder.append(this.mContext.getResources().getQuantityString(18087936, iccPinPukRetryCountSc, new Object[]{Integer.valueOf(iccPinPukRetryCountSc)}));
                        }
                    } else {
                        stringBuilder.append(this.mContext.getText(17039553));
                    }
                } else if (commandError == Error.SIM_PUK2) {
                    if (!TelBrand.IS_DCM) {
                        stringBuilder.append(this.mContext.getText(17039555));
                    } else if (this.mSc.equals(SC_PIN2)) {
                        stringBuilder.append(this.mContext.getText(17041208));
                    } else {
                        stringBuilder.append(this.mContext.getText(17039555));
                    }
                    stringBuilder.append("\n");
                    stringBuilder.append(this.mContext.getText(17039561));
                } else if (commandError == Error.REQUEST_NOT_SUPPORTED) {
                    if (TelBrand.IS_DCM) {
                        stringBuilder.append(this.mContext.getText(17039546));
                    } else if (this.mSc.equals(SC_PIN)) {
                        if (TelBrand.IS_SBM) {
                            stringBuilder.append(this.mContext.getText(17039546));
                        } else {
                            stringBuilder.append(this.mContext.getText(17039562));
                        }
                    }
                } else if (commandError == Error.FDN_CHECK_FAILURE) {
                    Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                    stringBuilder.append(this.mContext.getText(17039547));
                } else {
                    stringBuilder.append(getErrorMessage(asyncResult));
                }
                if (TelBrand.IS_DCM) {
                    this.mUssdCode = this.mSc + "*" + getErrorCode(asyncResult) + ACTION_DEACTIVATE;
                }
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
            if (TelBrand.IS_DCM && this.mSc.equals(SC_WAIT)) {
                this.mUssdCode = "43*5#";
            } else if (TelBrand.IS_DCM && this.mSc.equals(SC_BAICr)) {
                int[] iArr = (int[]) asyncResult.result;
                if (iArr.length == 0) {
                    this.mUssdCode = "351*5#";
                } else if ((iArr[0] & 2) != 0) {
                    this.mUssdCode = "351*5*0#";
                } else {
                    this.mUssdCode = "351*5#";
                }
            }
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(1);
            }
        } else if (isDeactivate()) {
            this.mState = State.COMPLETE;
            stringBuilder.append(this.mContext.getText(17039550));
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(2);
            } else if (TelBrand.IS_DCM && this.mSc.equals(SC_WAIT)) {
                this.mUssdCode = "43*4#";
            } else if (TelBrand.IS_DCM && this.mSc.equals(SC_BAICr)) {
                this.mUssdCode = "351*4#";
            }
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
            if (str.equals(SC_CFU)) {
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

    public void cancel() {
        if (this.mState != State.COMPLETE && this.mState != State.FAILED) {
            this.mState = State.CANCELLED;
            if (this.mIsPendingUSSD) {
                this.mPhone.mCi.cancelPendingUssd(obtainMessage(7, this));
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
        return TelBrand.IS_DCM ? this.mDialString : null;
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
        return TelBrand.IS_DCM ? this.mUssdCode : null;
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case 1:
                onSetComplete(message, (AsyncResult) message.obj);
                return;
            case 2:
                onGetClirComplete((AsyncResult) message.obj);
                return;
            case 3:
                onQueryCfComplete((AsyncResult) message.obj);
                return;
            case 4:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null) {
                    this.mState = State.FAILED;
                    this.mMessage = getErrorMessage(asyncResult);
                    this.mPhone.onMMIDone(this);
                    return;
                }
                return;
            case 5:
                onQueryComplete((AsyncResult) message.obj);
                return;
            case 6:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null && message.arg1 == 1) {
                    boolean z = message.arg2 == 1;
                    if (this.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, z, this.mDialingNumber);
                        this.mPhone.setCallForwardingPreference(z);
                    }
                }
                onSetComplete(message, asyncResult);
                return;
            case 7:
                if (this.mState != State.CANCELLED) {
                    this.mState = State.CANCELLED;
                }
                this.mPhone.onMMIDone(this);
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

    public boolean isSsInfo() {
        return this.mIsSsInfo;
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
                if (TelBrand.IS_DCM) {
                    this.mUssdCode = str;
                }
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
            if (!TelBrand.IS_DCM) {
                this.mMessage = this.mContext.getText(17039546);
            } else if ("148".equals(this.mSc)) {
                this.mMessage = this.mContext.getText(17041216);
            } else {
                this.mMessage = this.mContext.getText(17039546);
            }
            this.mPhone.onMMIDone(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onUssdRelease() {
        if (this.mState == State.PENDING) {
            this.mState = State.COMPLETE;
            this.mMessage = null;
            this.mPhone.onMMIDone(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void parseSsData(SsData ssData) {
        CommandException fromRilErrno = CommandException.fromRilErrno(ssData.result);
        this.mSc = getScStringFromScType(ssData.serviceType);
        this.mAction = getActionStringFromReqType(ssData.requestType);
        Rlog.d(LOG_TAG, "parseSsData msc = " + this.mSc + ", action = " + this.mAction + ", ex = " + fromRilErrno);
        switch (ssData.requestType) {
            case SS_ACTIVATION:
            case SS_DEACTIVATION:
            case SS_REGISTRATION:
            case SS_ERASURE:
                if (ssData.result == 0 && ssData.serviceType.isTypeUnConditional()) {
                    boolean z = (ssData.requestType == RequestType.SS_ACTIVATION || ssData.requestType == RequestType.SS_REGISTRATION) && isServiceClassVoiceorNone(ssData.serviceClass);
                    Rlog.d(LOG_TAG, "setVoiceCallForwardingFlag cffEnabled: " + z);
                    if (this.mPhone.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, z, null);
                        Rlog.d(LOG_TAG, "setVoiceCallForwardingFlag done from SS Info.");
                    } else {
                        Rlog.e(LOG_TAG, "setVoiceCallForwardingFlag aborted. sim records is null.");
                    }
                }
                onSetComplete(null, new AsyncResult(null, ssData.cfInfo, fromRilErrno));
                return;
            case SS_INTERROGATION:
                if (ssData.serviceType.isTypeClir()) {
                    Rlog.d(LOG_TAG, "CLIR INTERROGATION");
                    onGetClirComplete(new AsyncResult(null, ssData.ssInfo, fromRilErrno));
                    return;
                } else if (ssData.serviceType.isTypeCF()) {
                    Rlog.d(LOG_TAG, "CALL FORWARD INTERROGATION");
                    onQueryCfComplete(new AsyncResult(null, ssData.cfInfo, fromRilErrno));
                    return;
                } else {
                    onQueryComplete(new AsyncResult(null, ssData.ssInfo, fromRilErrno));
                    return;
                }
            default:
                Rlog.e(LOG_TAG, "Invaid requestType in SSData : " + ssData.requestType);
                return;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void processCode() {
        int i = 1;
        try {
            int scToCallForwardReason;
            String scToBarringFacility;
            String scToBarringFacility2;
            if (isShortCode()) {
                Rlog.d(LOG_TAG, "isShortCode");
                sendUssd(this.mDialingNumber);
            } else if (this.mDialingNumber != null) {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            } else if (this.mSc != null && this.mSc.equals(SC_CLIP)) {
                Rlog.d(LOG_TAG, "is CLIP");
                if (isInterrogate()) {
                    this.mPhone.mCi.queryCLIP(obtainMessage(5, this));
                    return;
                }
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            } else if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
                Rlog.d(LOG_TAG, "is CLIR");
                if (isInterrogate()) {
                    this.mPhone.mCi.getCLIR(obtainMessage(2, this));
                    return;
                }
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            } else if (isServiceCodeCallForwarding(this.mSc)) {
                Rlog.d(LOG_TAG, "is CF");
                String str = this.mSia;
                int siToServiceClass = siToServiceClass(this.mSib);
                scToCallForwardReason = scToCallForwardReason(this.mSc);
                int siToTime = siToTime(this.mSic);
                if (isInterrogate()) {
                    this.mPhone.mCi.queryCallForwardStatus(scToCallForwardReason, siToServiceClass, str, obtainMessage(3, this));
                    return;
                }
                int i2;
                if (isActivate()) {
                    if (isEmptyOrNull(str)) {
                        this.mIsCallFwdReg = false;
                        i2 = 1;
                    } else {
                        this.mIsCallFwdReg = true;
                        i2 = 3;
                    }
                } else if (isDeactivate()) {
                    i2 = 0;
                } else if (isRegister()) {
                    i2 = 3;
                } else if (isErasure()) {
                    i2 = 4;
                } else {
                    throw new RuntimeException("invalid action");
                }
                int i3 = ((scToCallForwardReason == 0 || scToCallForwardReason == 4) && ((siToServiceClass & 1) != 0 || siToServiceClass == 0)) ? 1 : 0;
                if (!(i2 == 1 || i2 == 3)) {
                    i = 0;
                }
                Rlog.d(LOG_TAG, "is CF setCallForward");
                this.mPhone.mCi.setCallForward(i2, scToCallForwardReason, siToServiceClass, str, siToTime, obtainMessage(6, i3, i, this));
            } else if (isServiceCodeCallBarring(this.mSc)) {
                String str2 = this.mSia;
                int siToServiceClass2 = siToServiceClass(this.mSib);
                scToBarringFacility = scToBarringFacility(this.mSc);
                if (isInterrogate()) {
                    this.mPhone.mCi.queryFacilityLock(scToBarringFacility, str2, siToServiceClass2, obtainMessage(5, this));
                } else if (isActivate() || isDeactivate()) {
                    this.mPhone.mCi.setFacilityLock(scToBarringFacility, isActivate(), str2, siToServiceClass2, obtainMessage(1, this));
                } else {
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            } else if (this.mSc != null && this.mSc.equals(SC_PWD)) {
                scToBarringFacility = this.mSib;
                String str3 = this.mSic;
                if (isActivate() || isRegister()) {
                    this.mAction = ACTION_REGISTER;
                    scToBarringFacility2 = this.mSia == null ? CommandsInterface.CB_FACILITY_BA_ALL : scToBarringFacility(this.mSia);
                    if (str3.equals(this.mPwd)) {
                        this.mPhone.mCi.changeBarringPassword(scToBarringFacility2, scToBarringFacility, str3, obtainMessage(1, this));
                        return;
                    } else {
                        handlePasswordError(17039553);
                        return;
                    }
                }
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            } else if (this.mSc != null && this.mSc.equals(SC_WAIT)) {
                int siToServiceClass3 = siToServiceClass(this.mSia);
                if (isActivate() || isDeactivate()) {
                    this.mPhone.mCi.setCallWaiting(isActivate(), siToServiceClass3, obtainMessage(1, this));
                } else if (isInterrogate()) {
                    this.mPhone.mCi.queryCallWaiting(siToServiceClass3, obtainMessage(5, this));
                } else {
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            } else if (isPinPukCommand()) {
                scToBarringFacility2 = this.mSia;
                scToBarringFacility = this.mSib;
                scToCallForwardReason = scToBarringFacility.length();
                if (!isRegister()) {
                    throw new RuntimeException("Ivalid register/action=" + this.mAction);
                } else if (scToBarringFacility.equals(this.mSic)) {
                    if (scToCallForwardReason < 4 || scToCallForwardReason > 8) {
                        if (!TelBrand.IS_DCM) {
                            handlePasswordError(17039558);
                        } else if (this.mSc.equals(SC_PIN2)) {
                            handlePasswordError(17041210);
                        } else {
                            handlePasswordError(17039558);
                        }
                    } else if (!this.mSc.equals(SC_PIN) || this.mUiccApplication == null || this.mUiccApplication.getState() != AppState.APPSTATE_PUK) {
                        if (TelBrand.IS_DCM) {
                            if (this.mPhone.mCi.getRadioState().isOn()) {
                                Rlog.d(LOG_TAG, "changeIccLockPassword radio:on, service code = " + this.mSc);
                            } else {
                                Rlog.d(LOG_TAG, "changeIccLockPassword radio:off, service code = " + this.mSc);
                                throw new RuntimeException("invalid action");
                            }
                        }
                        if (this.mUiccApplication != null) {
                            Rlog.d(LOG_TAG, "process mmi service code using UiccApp sc=" + this.mSc);
                            if (this.mSc.equals(SC_PIN)) {
                                this.mUiccApplication.changeIccLockPassword(scToBarringFacility2, scToBarringFacility, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PIN2)) {
                                this.mUiccApplication.changeIccFdnPassword(scToBarringFacility2, scToBarringFacility, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PUK)) {
                                this.mUiccApplication.supplyPuk(scToBarringFacility2, scToBarringFacility, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PUK2)) {
                                this.mUiccApplication.supplyPuk2(scToBarringFacility2, scToBarringFacility, obtainMessage(1, this));
                                return;
                            } else {
                                throw new RuntimeException("uicc unsupported service code=" + this.mSc);
                            }
                        }
                        throw new RuntimeException("No application mUiccApplicaiton is null");
                    } else if (!TelBrand.IS_DCM) {
                        handlePasswordError(17039560);
                    } else if (this.mUiccApplication.getPin1State() != PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                        handlePasswordError(17039560);
                    } else {
                        handlePasswordError(17041211);
                    }
                } else if (!TelBrand.IS_DCM) {
                    handlePasswordError(17039557);
                } else if (this.mSc.equals(SC_PIN2)) {
                    handlePasswordError(17041209);
                } else {
                    handlePasswordError(17039557);
                }
            } else if (this.mPoundString != null) {
                sendUssd(this.mPoundString);
            } else {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
        } catch (RuntimeException e) {
            this.mState = State.FAILED;
            if (TelBrand.IS_DCM) {
                this.mMessage = this.mContext.getText(17041217);
            } else {
                this.mMessage = this.mContext.getText(17039546);
            }
            this.mPhone.onMMIDone(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void processSsData(AsyncResult asyncResult) {
        Rlog.d(LOG_TAG, "In processSsData");
        this.mIsSsInfo = true;
        try {
            parseSsData((SsData) asyncResult.result);
        } catch (ClassCastException e) {
            Rlog.e(LOG_TAG, "Class Cast Exception in parsing SS Data : " + e);
        } catch (NullPointerException e2) {
            Rlog.e(LOG_TAG, "Null Pointer Exception in parsing SS Data : " + e2);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendUssd(String str) {
        if (!TelBrand.IS_DCM || str == null || !str.equals("*143#") || this.mPhone.mSmsStorageMonitor.isStorageAvailable()) {
            this.mIsPendingUSSD = true;
            this.mPhone.mCi.sendUSSD(str, obtainMessage(4, this));
            return;
        }
        Rlog.i(LOG_TAG, "The storage is not available, so cannot send ussd.");
        this.mState = State.FAILED;
        this.mMessage = this.mContext.getText(17039546);
        this.mPhone.onMMIDone(this);
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("GsmMmiCode {");
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
