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
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccRecords;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private MmiCode.State mState = MmiCode.State.PENDING;

    /* JADX INFO: Access modifiers changed from: package-private */
    public static ImsPhoneMmiCode newFromDialString(String dialString, ImsPhone phone) {
        Matcher m = sPatternSuppService.matcher(dialString);
        if (m.matches()) {
            ImsPhoneMmiCode ret = new ImsPhoneMmiCode(phone);
            ret.mPoundString = makeEmptyNull(m.group(1));
            ret.mAction = makeEmptyNull(m.group(2));
            ret.mSc = makeEmptyNull(m.group(3));
            ret.mSia = makeEmptyNull(m.group(5));
            ret.mSib = makeEmptyNull(m.group(7));
            ret.mSic = makeEmptyNull(m.group(9));
            ret.mPwd = makeEmptyNull(m.group(11));
            ret.mDialingNumber = makeEmptyNull(m.group(12));
            if (ret.mDialingNumber == null || !ret.mDialingNumber.endsWith(ACTION_DEACTIVATE) || !dialString.endsWith(ACTION_DEACTIVATE)) {
                return ret;
            }
            ImsPhoneMmiCode ret2 = new ImsPhoneMmiCode(phone);
            ret2.mPoundString = dialString;
            return ret2;
        } else if (dialString.endsWith(ACTION_DEACTIVATE)) {
            ImsPhoneMmiCode ret3 = new ImsPhoneMmiCode(phone);
            ret3.mPoundString = dialString;
            return ret3;
        } else if (isTwoDigitShortCode(phone.getContext(), dialString) || !isShortCode(dialString, phone)) {
            return null;
        } else {
            ImsPhoneMmiCode ret4 = new ImsPhoneMmiCode(phone);
            ret4.mDialingNumber = dialString;
            return ret4;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static ImsPhoneMmiCode newNetworkInitiatedUssd(String ussdMessage, boolean isUssdRequest, ImsPhone phone) {
        ImsPhoneMmiCode ret = new ImsPhoneMmiCode(phone);
        ret.mMessage = ussdMessage;
        ret.mIsUssdRequest = isUssdRequest;
        if (isUssdRequest) {
            ret.mIsPendingUSSD = true;
            ret.mState = MmiCode.State.PENDING;
        } else {
            ret.mState = MmiCode.State.COMPLETE;
        }
        return ret;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static ImsPhoneMmiCode newFromUssdUserInput(String ussdMessge, ImsPhone phone) {
        ImsPhoneMmiCode ret = new ImsPhoneMmiCode(phone);
        ret.mMessage = ussdMessge;
        ret.mState = MmiCode.State.PENDING;
        ret.mIsPendingUSSD = true;
        return ret;
    }

    private static String makeEmptyNull(String s) {
        if (s == null || s.length() != 0) {
            return s;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static boolean isScMatchesSuppServType(String dialString) {
        Matcher m = sPatternSuppService.matcher(dialString);
        if (!m.matches()) {
            return false;
        }
        String sc = makeEmptyNull(m.group(3));
        if (!sc.equals(SC_CFUT) && !sc.equals(SC_BS_MT)) {
            return false;
        }
        return true;
    }

    private static boolean isEmptyOrNull(CharSequence s) {
        return s == null || s.length() == 0;
    }

    private static int scToCallForwardReason(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call forward sc");
        } else if (sc.equals(SC_CF_All)) {
            return 4;
        } else {
            if (sc.equals(SC_CFU) || sc.equals(SC_CFUT)) {
                return 0;
            }
            if (sc.equals(SC_CFB)) {
                return 1;
            }
            if (sc.equals(SC_CFNR)) {
                return 3;
            }
            if (sc.equals(SC_CFNRy)) {
                return 2;
            }
            if (sc.equals(SC_CF_All_Conditional)) {
                return 5;
            }
            throw new RuntimeException("invalid call forward sc");
        }
    }

    private static int siToServiceClass(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        switch (Integer.parseInt(si, 10)) {
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
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                return 16;
            case 25:
                return 32;
            case 26:
                return 17;
            case 99:
                return 64;
            default:
                throw new RuntimeException("unsupported MMI service code " + si);
        }
    }

    private static int siToTime(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        return Integer.parseInt(si, 10);
    }

    static boolean isServiceCodeCallForwarding(String sc) {
        return sc != null && (sc.equals(SC_CFU) || sc.equals(SC_CFB) || sc.equals(SC_CFNRy) || sc.equals(SC_CFNR) || sc.equals(SC_CF_All) || sc.equals(SC_CF_All_Conditional));
    }

    static boolean isServiceCodeUncondCallFwdTimer(String sc) {
        return sc != null && sc.equals(SC_CFUT);
    }

    static boolean isServiceCodeCallBarring(String sc) {
        String[] barringMMI;
        Resources resource = Resources.getSystem();
        if (!(sc == null || (barringMMI = resource.getStringArray(17236022)) == null)) {
            for (String match : barringMMI) {
                if (sc.equals(match)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String scToBarringFacility(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call barring sc");
        } else if (sc.equals(SC_BAOC)) {
            return CommandsInterface.CB_FACILITY_BAOC;
        } else {
            if (sc.equals(SC_BAOIC)) {
                return CommandsInterface.CB_FACILITY_BAOIC;
            }
            if (sc.equals(SC_BAOICxH)) {
                return CommandsInterface.CB_FACILITY_BAOICxH;
            }
            if (sc.equals(SC_BAIC)) {
                return CommandsInterface.CB_FACILITY_BAIC;
            }
            if (sc.equals(SC_BAICr)) {
                return CommandsInterface.CB_FACILITY_BAICr;
            }
            if (sc.equals(SC_BA_ALL)) {
                return CommandsInterface.CB_FACILITY_BA_ALL;
            }
            if (sc.equals(SC_BA_MO)) {
                return CommandsInterface.CB_FACILITY_BA_MO;
            }
            if (sc.equals(SC_BA_MT)) {
                return CommandsInterface.CB_FACILITY_BA_MT;
            }
            throw new RuntimeException("invalid call barring sc");
        }
    }

    ImsPhoneMmiCode(ImsPhone phone) {
        super(phone.getHandler().getLooper());
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mIccRecords = this.mPhone.mDefaultPhone.mIccRecords.get();
    }

    @Override // com.android.internal.telephony.MmiCode
    public MmiCode.State getState() {
        return this.mState;
    }

    @Override // com.android.internal.telephony.MmiCode
    public CharSequence getMessage() {
        return this.mMessage;
    }

    @Override // com.android.internal.telephony.MmiCode
    public Phone getPhone() {
        return this.mPhone;
    }

    @Override // com.android.internal.telephony.MmiCode
    public void cancel() {
        if (this.mState != MmiCode.State.COMPLETE && this.mState != MmiCode.State.FAILED) {
            this.mState = MmiCode.State.CANCELLED;
            if (this.mIsPendingUSSD) {
                this.mPhone.cancelUSSD();
            } else {
                this.mPhone.onMMIDone(this);
            }
        }
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isCancelable() {
        return this.mIsPendingUSSD;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getDialingNumber() {
        return this.mDialingNumber;
    }

    boolean isMMI() {
        return this.mPoundString != null;
    }

    boolean isShortCode() {
        return this.mPoundString == null && this.mDialingNumber != null && this.mDialingNumber.length() <= 2;
    }

    private static boolean isTwoDigitShortCode(Context context, String dialString) {
        Rlog.d(LOG_TAG, "isTwoDigitShortCode");
        if (dialString == null || dialString.length() > 2) {
            return false;
        }
        if (sTwoDigitNumberPattern == null) {
            sTwoDigitNumberPattern = context.getResources().getStringArray(17236010);
        }
        String[] arr$ = sTwoDigitNumberPattern;
        for (String dialnumber : arr$) {
            Rlog.d(LOG_TAG, "Two Digit Number Pattern " + dialnumber);
            if (dialString.equals(dialnumber)) {
                Rlog.d(LOG_TAG, "Two Digit Number Pattern -true");
                return true;
            }
        }
        Rlog.d(LOG_TAG, "Two Digit Number Pattern -false");
        return false;
    }

    private static boolean isShortCode(String dialString, ImsPhone phone) {
        if (dialString == null || dialString.length() == 0 || PhoneNumberUtils.isLocalEmergencyNumber(phone.getContext(), dialString)) {
            return false;
        }
        return isShortCodeUSSD(dialString, phone);
    }

    private static boolean isShortCodeUSSD(String dialString, ImsPhone phone) {
        return (dialString == null || dialString.length() > 2 || (!phone.isInCall() && dialString.length() == 2 && dialString.charAt(0) == '1')) ? false : true;
    }

    boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isTemporaryModeCLIR() {
        return this.mSc != null && this.mSc.equals(SC_CLIR) && this.mDialingNumber != null && (isActivate() || isDeactivate());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
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

    boolean isActivate() {
        return this.mAction != null && this.mAction.equals("*");
    }

    boolean isDeactivate() {
        return this.mAction != null && this.mAction.equals(ACTION_DEACTIVATE);
    }

    boolean isInterrogate() {
        return this.mAction != null && this.mAction.equals(ACTION_INTERROGATE);
    }

    boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    boolean isErasure() {
        return this.mAction != null && this.mAction.equals(ACTION_ERASURE);
    }

    public boolean isPendingUSSD() {
        return this.mIsPendingUSSD;
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isUssdRequest() {
        return this.mIsUssdRequest;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isSupportedOverImsPhone() {
        if (!TelBrand.IS_DCM) {
            if (isShortCode()) {
                return true;
            }
            if (this.mDialingNumber != null) {
                return false;
            }
            if (isServiceCodeCallForwarding(this.mSc) || isServiceCodeCallBarring(this.mSc) || ((this.mSc != null && this.mSc.equals(SC_WAIT)) || ((this.mSc != null && this.mSc.equals(SC_CLIR)) || ((this.mSc != null && this.mSc.equals(SC_CLIP)) || ((this.mSc != null && this.mSc.equals(SC_COLR)) || ((this.mSc != null && this.mSc.equals(SC_COLP)) || ((this.mSc != null && this.mSc.equals(SC_BS_MT)) || (this.mSc != null && this.mSc.equals(SC_BAICa))))))))) {
                int serviceClass = siToServiceClass(this.mSib);
                return serviceClass == 0 || serviceClass == 1;
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

    public int calForwardAction(String dialingNumber) {
        if (isActivate()) {
            if (isEmptyOrNull(dialingNumber)) {
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

    public int callBarrAction(String dialingNumber) {
        if (isActivate()) {
            return 1;
        }
        if (isDeactivate()) {
            return 0;
        }
        if (isRegister()) {
            if (!isEmptyOrNull(dialingNumber)) {
                return 3;
            }
            throw new RuntimeException("invalid action");
        } else if (isErasure()) {
            return 4;
        } else {
            throw new RuntimeException("invalid action");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void processCode() throws CallStateException {
        try {
        } catch (RuntimeException e) {
            this.mState = MmiCode.State.FAILED;
            this.mMessage = this.mContext.getText(17039546);
            this.mPhone.onMMIDone(this);
        }
        if (isShortCode()) {
            Rlog.d(LOG_TAG, "isShortCode");
            Rlog.d(LOG_TAG, "Sending short code '" + this.mDialingNumber + "' over CS pipe.");
            throw new CallStateException(ImsPhone.CS_FALLBACK);
        } else if (isServiceCodeCallForwarding(this.mSc)) {
            Rlog.d(LOG_TAG, "is CF");
            String dialingNumber = this.mSia;
            int reason = scToCallForwardReason(this.mSc);
            int serviceClass = siToServiceClass(this.mSib);
            int time = siToTime(this.mSic);
            if (isInterrogate()) {
                this.mPhone.getCallForwardingOption(reason, obtainMessage(1, this));
                return;
            }
            int cfAction = calForwardAction(dialingNumber);
            int isSettingUnconditional = (reason == 0 || reason == 4) ? 1 : 0;
            int isEnableDesired = (cfAction == 1 || cfAction == 3) ? 1 : 0;
            Rlog.d(LOG_TAG, "is CF setCallForward");
            this.mPhone.setCallForwardingOption(cfAction, reason, dialingNumber, serviceClass, time, obtainMessage(4, isSettingUnconditional, isEnableDesired, this));
        } else if (isServiceCodeUncondCallFwdTimer(this.mSc)) {
            Rlog.d(LOG_TAG, "is UncondCFTimer");
            String dialingNumber2 = this.mSia;
            int reason2 = scToCallForwardReason(this.mSc);
            if (isInterrogate()) {
                this.mPhone.getCallForwardingOption(reason2, obtainMessage(8, this));
                return;
            }
            int startHour = 0;
            int startMinute = 0;
            int endHour = 0;
            int endMinute = 0;
            if (this.mSic != null) {
                String[] duration = this.mSic.split("\\$");
                if (!(duration[0] == null || duration[1] == null)) {
                    String[] start = duration[0].split("\\:");
                    if (start[0] != null) {
                        startHour = Integer.parseInt(start[0]);
                    }
                    if (start[1] != null) {
                        startMinute = Integer.parseInt(start[1]);
                    }
                    String[] end = duration[1].split("\\:");
                    if (end[0] != null) {
                        endHour = Integer.parseInt(end[0]);
                    }
                    if (end[1] != null) {
                        endMinute = Integer.parseInt(end[1]);
                    }
                }
            }
            int cfTimerAction = calForwardAction(dialingNumber2);
            Rlog.d(LOG_TAG, "is CFUT setCallForward");
            this.mPhone.setCallForwardingUncondTimerOption(startHour, startMinute, endHour, endMinute, cfTimerAction, reason2, dialingNumber2, obtainMessage(9, this));
        } else if (isServiceCodeCallBarring(this.mSc)) {
            String password = this.mSia;
            String facility = scToBarringFacility(this.mSc);
            if (isInterrogate()) {
                this.mPhone.getCallBarring(facility, obtainMessage(7, this));
            } else if (isActivate() || isDeactivate()) {
                this.mPhone.setCallBarring(facility, isActivate(), password, obtainMessage(0, this));
            } else {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
        } else {
            if (this.mSc == null || !this.mSc.equals(SC_CLIR)) {
                if (this.mSc == null || !this.mSc.equals(SC_CLIP)) {
                    if (this.mSc == null || !this.mSc.equals(SC_COLP)) {
                        if (this.mSc == null || !this.mSc.equals(SC_COLR)) {
                            if (this.mSc != null && this.mSc.equals(SC_BS_MT)) {
                                try {
                                    if (isInterrogate()) {
                                        this.mPhone.mCT.getUtInterface().queryCallBarring(10, obtainMessage(10, this));
                                    } else {
                                        processIcbMmiCodeForUpdate();
                                    }
                                    return;
                                } catch (ImsException e2) {
                                    Rlog.d(LOG_TAG, "Could not get UT handle for ICB.");
                                    return;
                                }
                            } else if (this.mSc != null && this.mSc.equals(SC_BAICa)) {
                                int callAction = 0;
                                try {
                                    if (isInterrogate()) {
                                        this.mPhone.mCT.getUtInterface().queryCallBarring(6, obtainMessage(10, this));
                                        return;
                                    }
                                    if (isActivate()) {
                                        callAction = 1;
                                    } else if (isDeactivate()) {
                                        callAction = 0;
                                    }
                                    this.mPhone.mCT.getUtInterface().updateCallBarring(6, callAction, obtainMessage(0, this), (String[]) null);
                                    return;
                                } catch (ImsException e3) {
                                    Rlog.d(LOG_TAG, "Could not get UT handle for ICBa.");
                                    return;
                                }
                            } else if (this.mSc != null && this.mSc.equals(SC_WAIT)) {
                                int serviceClass2 = siToServiceClass(this.mSib);
                                if (isActivate() || isDeactivate()) {
                                    this.mPhone.setCallWaiting(isActivate(), serviceClass2, obtainMessage(0, this));
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
                        } else if (isActivate()) {
                            try {
                                this.mPhone.mCT.getUtInterface().updateCOLR(1, obtainMessage(0, this));
                                return;
                            } catch (ImsException e4) {
                                Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLR.");
                                return;
                            }
                        } else if (isDeactivate()) {
                            try {
                                this.mPhone.mCT.getUtInterface().updateCOLR(0, obtainMessage(0, this));
                                return;
                            } catch (ImsException e5) {
                                Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLR.");
                                return;
                            }
                        } else if (isInterrogate()) {
                            try {
                                this.mPhone.mCT.getUtInterface().queryCOLR(obtainMessage(7, this));
                                return;
                            } catch (ImsException e6) {
                                Rlog.d(LOG_TAG, "Could not get UT handle for queryCOLR.");
                                return;
                            }
                        } else {
                            throw new RuntimeException("Invalid or Unsupported MMI Code");
                        }
                    } else if (isInterrogate()) {
                        try {
                            this.mPhone.mCT.getUtInterface().queryCOLP(obtainMessage(7, this));
                            return;
                        } catch (ImsException e7) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for queryCOLP.");
                            return;
                        }
                    } else if (isActivate() || isDeactivate()) {
                        try {
                            this.mPhone.mCT.getUtInterface().updateCOLP(isActivate(), obtainMessage(0, this));
                            return;
                        } catch (ImsException e8) {
                            Rlog.d(LOG_TAG, "Could not get UT handle for updateCOLP.");
                            return;
                        }
                    } else {
                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                    }
                } else if (isInterrogate()) {
                    try {
                        this.mPhone.mCT.getUtInterface().queryCLIP(obtainMessage(7, this));
                        return;
                    } catch (ImsException e9) {
                        Rlog.d(LOG_TAG, "Could not get UT handle for queryCLIP.");
                        return;
                    }
                } else if (isActivate() || isDeactivate()) {
                    try {
                        this.mPhone.mCT.getUtInterface().updateCLIP(isActivate(), obtainMessage(0, this));
                        return;
                    } catch (ImsException e10) {
                        Rlog.d(LOG_TAG, "Could not get UT handle for updateCLIP.");
                        return;
                    }
                } else {
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            } else if (isInterrogate()) {
                try {
                    this.mPhone.mCT.getUtInterface().queryCLIR(obtainMessage(6, this));
                    return;
                } catch (ImsException e11) {
                    Rlog.d(LOG_TAG, "Could not get UT handle for queryCLIR.");
                    return;
                }
            } else {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
            this.mState = MmiCode.State.FAILED;
            this.mMessage = this.mContext.getText(17039546);
            this.mPhone.onMMIDone(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onUssdFinished(String ussdMessage, boolean isUssdRequest) {
        if (this.mState == MmiCode.State.PENDING) {
            if (ussdMessage == null) {
                this.mMessage = this.mContext.getText(17039554);
            } else {
                this.mMessage = ussdMessage;
            }
            this.mIsUssdRequest = isUssdRequest;
            if (!isUssdRequest) {
                this.mState = MmiCode.State.COMPLETE;
            }
            this.mPhone.onMMIDone(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onUssdFinishedError() {
        if (this.mState == MmiCode.State.PENDING) {
            this.mState = MmiCode.State.FAILED;
            this.mMessage = this.mContext.getText(17039546);
            this.mPhone.onMMIDone(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void sendUssd(String ussdMessage) {
        this.mIsPendingUSSD = true;
        this.mPhone.sendUSSD(ussdMessage, obtainMessage(2, this));
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        boolean cffEnabled;
        switch (msg.what) {
            case 0:
                onSetComplete(msg, (AsyncResult) msg.obj);
                return;
            case 1:
                onQueryCfComplete((AsyncResult) msg.obj);
                return;
            case 2:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    this.mState = MmiCode.State.FAILED;
                    this.mMessage = getErrorMessage(ar);
                    this.mPhone.onMMIDone(this);
                    return;
                }
                return;
            case 3:
                onQueryComplete((AsyncResult) msg.obj);
                return;
            case 4:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception == null && msg.arg1 == 1) {
                    if (msg.arg2 == 1) {
                        cffEnabled = true;
                    } else {
                        cffEnabled = false;
                    }
                    if (this.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, this.mDialingNumber);
                        this.mPhone.setCallForwardingPreference(cffEnabled);
                    }
                }
                onSetComplete(msg, ar2);
                this.mPhone.updateCallForwardStatus();
                return;
            case 5:
                this.mPhone.onMMIDone(this);
                return;
            case 6:
                onQueryClirComplete((AsyncResult) msg.obj);
                return;
            case 7:
                onSuppSvcQueryComplete((AsyncResult) msg.obj);
                return;
            case 8:
                onQueryUncondCfTimerComplete((AsyncResult) msg.obj);
                return;
            case 9:
                onSetComplete(msg, (AsyncResult) msg.obj);
                return;
            case 10:
                onIcbQueryComplete((AsyncResult) msg.obj);
                return;
            default:
                return;
        }
    }

    private void processIcbMmiCodeForUpdate() {
        String dialingNumber = this.mSia;
        String[] icbNum = null;
        if (dialingNumber != null) {
            icbNum = dialingNumber.split("\\$");
        }
        try {
            this.mPhone.mCT.getUtInterface().updateCallBarring(10, callBarrAction(dialingNumber), obtainMessage(0, this), icbNum);
        } catch (ImsException e) {
            Rlog.d(LOG_TAG, "Could not get UT handle for updating ICB.");
        }
    }

    private CharSequence getErrorMessage(AsyncResult ar) {
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

    private void onSetComplete(Message msg, AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (!(ar.exception instanceof CommandException)) {
                ImsException error = ar.exception;
                if (error.getMessage() != null) {
                    sb.append(error.getMessage());
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else if (((CommandException) ar.exception).getCommandError() == CommandException.Error.PASSWORD_INCORRECT) {
                sb.append(this.mContext.getText(17039553));
            } else {
                sb.append(this.mContext.getText(17039546));
            }
        } else if (isActivate()) {
            this.mState = MmiCode.State.COMPLETE;
            if (this.mIsCallFwdReg) {
                sb.append(this.mContext.getText(17039551));
            } else {
                sb.append(this.mContext.getText(17039548));
            }
        } else if (isDeactivate()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(17039550));
        } else if (isRegister()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(17039551));
        } else if (isErasure()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(17039552));
        } else {
            this.mState = MmiCode.State.FAILED;
            sb.append(this.mContext.getText(17039546));
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence serviceClassToCFString(int serviceClass) {
        switch (serviceClass) {
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

    private CharSequence makeCFQueryResultMessage(CallForwardInfo info, int serviceClassMask) {
        CharSequence template;
        String[] sources = {"{0}", "{1}", "{2}"};
        CharSequence[] destinations = new CharSequence[3];
        boolean needTimeTemplate = info.reason == 2;
        if (info.status == 1) {
            if (needTimeTemplate) {
                template = this.mContext.getText(17039623);
            } else {
                template = this.mContext.getText(17039622);
            }
        } else if (info.status == 0 && isEmptyOrNull(info.number)) {
            template = this.mContext.getText(17039621);
        } else if (needTimeTemplate) {
            template = this.mContext.getText(17039625);
        } else {
            template = this.mContext.getText(17039624);
        }
        destinations[0] = serviceClassToCFString(info.serviceClass & serviceClassMask);
        destinations[1] = PhoneNumberUtils.stringFromStringAndTOA(info.number, info.toa);
        destinations[2] = Integer.toString(info.timeSeconds);
        if (info.reason == 0 && (info.serviceClass & serviceClassMask) == 1) {
            boolean cffEnabled = info.status == 1;
            if (this.mIccRecords != null) {
                this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, info.number);
                Rlog.d(LOG_TAG, "makeCFQueryResultMessage cffEnabled  = " + cffEnabled);
                this.mPhone.setCallForwardingPreference(cffEnabled);
            }
        }
        return TextUtils.replace(template, sources, destinations);
    }

    private void onQueryCfComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof ImsException) {
                ImsException error = ar.exception;
                if (error.getMessage() != null) {
                    sb.append(error.getMessage());
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else {
                sb.append(getErrorMessage(ar));
            }
        } else {
            CallForwardInfo[] infos = (CallForwardInfo[]) ar.result;
            if (infos.length == 0) {
                sb.append(this.mContext.getText(17039550));
                if (this.mIccRecords != null) {
                    this.mPhone.setCallForwardingPreference(false);
                    this.mIccRecords.setVoiceCallForwardingFlag(1, false, null);
                }
            } else {
                SpannableStringBuilder tb = new SpannableStringBuilder();
                for (int serviceClassMask = 1; serviceClassMask <= 128; serviceClassMask <<= 1) {
                    int s = infos.length;
                    for (int i = 0; i < s; i++) {
                        if ((infos[i].serviceClass & serviceClassMask) != 0) {
                            tb.append(makeCFQueryResultMessage(infos[i], serviceClassMask));
                            tb.append((CharSequence) "\n");
                        }
                    }
                }
                sb.append((CharSequence) tb);
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryUncondCfTimerComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            sb.append(getErrorMessage(ar));
        } else {
            CallForwardInfo[] infos = (CallForwardInfo[]) ar.result;
            if (infos.length == 0) {
                Rlog.d(LOG_TAG, "In infos.length == 0");
                sb.append(this.mContext.getText(17039550));
            } else {
                SpannableStringBuilder tb = new SpannableStringBuilder();
                for (int serviceClassMask = 1; serviceClassMask <= 128; serviceClassMask <<= 1) {
                    int s = infos.length;
                    for (int i = 0; i < s; i++) {
                        if ((infos[i].serviceClass & serviceClassMask) == 1 && infos[i].status != 0) {
                            sb.append("StartTime: " + infos[i].startHour + ":" + infos[i].startMinute + "\n");
                            sb.append("EndTime: " + infos[i].endHour + ":" + infos[i].endMinute + "\n");
                            sb.append("Service:" + ((Object) this.mContext.getText(17039599)));
                        } else if ((infos[i].serviceClass & serviceClassMask) != 0) {
                            tb.append(makeCFTQueryResultMessage(infos[i], serviceClassMask));
                            tb.append((CharSequence) "\n");
                        }
                    }
                }
                sb.append((CharSequence) tb);
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence makeCFTQueryResultMessage(CallForwardInfo info, int serviceClassMask) {
        CharSequence template;
        Rlog.d(LOG_TAG, "makeCFTQueryResultMessage: ");
        String[] sources = {"{0}", "{1}", "{2}"};
        CharSequence[] destinations = new CharSequence[3];
        if (info.status != 0 || !isEmptyOrNull(info.number)) {
            template = this.mContext.getText(17039624);
        } else {
            template = this.mContext.getText(17039621);
        }
        destinations[0] = serviceClassToCFString(info.serviceClass & serviceClassMask);
        destinations[1] = PhoneNumberUtils.stringFromStringAndTOA(info.number, info.toa);
        destinations[2] = Integer.toString(info.timeSeconds);
        return TextUtils.replace(template, sources, destinations);
    }

    private void onSuppSvcQueryComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof ImsException) {
                ImsException error = ar.exception;
                if (error.getMessage() != null) {
                    sb.append(error.getMessage());
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else {
                sb.append(getErrorMessage(ar));
            }
        } else {
            this.mState = MmiCode.State.FAILED;
            if (ar.result instanceof Bundle) {
                Rlog.d(LOG_TAG, "Received CLIP/COLP/COLR Response.");
                ImsSsInfo ssInfo = ((Bundle) ar.result).getParcelable(UT_BUNDLE_KEY_SSINFO);
                if (ssInfo != null) {
                    Rlog.d(LOG_TAG, "ImsSsInfo mStatus = " + ssInfo.mStatus);
                    if (ssInfo.mStatus == 0) {
                        sb.append(this.mContext.getText(17039550));
                        this.mState = MmiCode.State.COMPLETE;
                    } else if (ssInfo.mStatus == 1) {
                        sb.append(this.mContext.getText(17039548));
                        this.mState = MmiCode.State.COMPLETE;
                    } else {
                        sb.append(this.mContext.getText(17039546));
                    }
                } else {
                    sb.append(this.mContext.getText(17039546));
                }
            } else {
                Rlog.d(LOG_TAG, "Received Call Barring Response.");
                if (((int[]) ar.result)[0] == 1) {
                    sb.append(this.mContext.getText(17039548));
                    this.mState = MmiCode.State.COMPLETE;
                } else {
                    sb.append(this.mContext.getText(17039550));
                    this.mState = MmiCode.State.COMPLETE;
                }
            }
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onIcbQueryComplete(AsyncResult ar) {
        Rlog.d(LOG_TAG, "onIcbQueryComplete ");
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof ImsException) {
                ImsException error = ar.exception;
                if (error.getMessage() != null) {
                    sb.append(error.getMessage());
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else {
                sb.append(getErrorMessage(ar));
            }
        } else {
            ImsSsInfo[] infos = (ImsSsInfo[]) ar.result;
            if (infos.length == 0) {
                sb.append(this.mContext.getText(17039550));
            } else {
                int s = infos.length;
                for (int i = 0; i < s; i++) {
                    if (infos[i].mIcbNum != null) {
                        sb.append("Num: " + infos[i].mIcbNum + " status: " + infos[i].mStatus + "\n");
                    } else if (infos[i].mStatus == 1) {
                        sb.append(this.mContext.getText(17039548));
                    } else {
                        sb.append(this.mContext.getText(17039550));
                    }
                }
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryClirComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        this.mState = MmiCode.State.FAILED;
        if (ar.exception == null) {
            int[] clirInfo = ((Bundle) ar.result).getIntArray(UT_BUNDLE_KEY_CLIR);
            Rlog.d(LOG_TAG, "CLIR param n=" + clirInfo[0] + " m=" + clirInfo[1]);
            switch (clirInfo[1]) {
                case 0:
                    sb.append(this.mContext.getText(17039584));
                    this.mState = MmiCode.State.COMPLETE;
                    break;
                case 1:
                    sb.append(this.mContext.getText(17039585));
                    this.mState = MmiCode.State.COMPLETE;
                    break;
                case 2:
                default:
                    sb.append(this.mContext.getText(17039546));
                    this.mState = MmiCode.State.FAILED;
                    break;
                case 3:
                    switch (clirInfo[0]) {
                        case 0:
                            sb.append(this.mContext.getText(17039580));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        case 1:
                            sb.append(this.mContext.getText(17039580));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        case 2:
                            sb.append(this.mContext.getText(17039581));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        default:
                            sb.append(this.mContext.getText(17039546));
                            this.mState = MmiCode.State.FAILED;
                            break;
                    }
                case 4:
                    switch (clirInfo[0]) {
                        case 0:
                            sb.append(this.mContext.getText(17039583));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        case 1:
                            sb.append(this.mContext.getText(17039582));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        case 2:
                            sb.append(this.mContext.getText(17039583));
                            this.mState = MmiCode.State.COMPLETE;
                            break;
                        default:
                            sb.append(this.mContext.getText(17039546));
                            this.mState = MmiCode.State.FAILED;
                            break;
                    }
            }
        } else if (ar.exception instanceof ImsException) {
            ImsException error = ar.exception;
            if (error.getMessage() != null) {
                sb.append(error.getMessage());
            } else {
                sb.append(getErrorMessage(ar));
            }
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof ImsException) {
                ImsException error = ar.exception;
                if (error.getMessage() != null) {
                    sb.append(error.getMessage());
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else {
                sb.append(getErrorMessage(ar));
            }
        } else {
            int[] ints = (int[]) ar.result;
            if (ints.length == 0) {
                sb.append(this.mContext.getText(17039546));
            } else if (ints[0] == 0) {
                sb.append(this.mContext.getText(17039550));
            } else if (this.mSc.equals(SC_WAIT)) {
                sb.append(createQueryCallWaitingResultMessage(ints[1]));
            } else if (ints[0] == 1) {
                sb.append(this.mContext.getText(17039548));
            } else {
                sb.append(this.mContext.getText(17039546));
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence createQueryCallWaitingResultMessage(int serviceClass) {
        StringBuilder sb = new StringBuilder(this.mContext.getText(17039549));
        for (int classMask = 1; classMask <= 128; classMask <<= 1) {
            if ((classMask & serviceClass) != 0) {
                sb.append("\n");
                sb.append(serviceClassToCFString(classMask & serviceClass));
            }
        }
        return sb;
    }

    @Override // android.os.Handler
    public String toString() {
        StringBuilder sb = new StringBuilder("ImsPhoneMmiCode {");
        sb.append("State=" + getState());
        if (this.mAction != null) {
            sb.append(" action=" + this.mAction);
        }
        if (this.mSc != null) {
            sb.append(" sc=" + this.mSc);
        }
        if (this.mSia != null) {
            sb.append(" sia=" + this.mSia);
        }
        if (this.mSib != null) {
            sb.append(" sib=" + this.mSib);
        }
        if (this.mSic != null) {
            sb.append(" sic=" + this.mSic);
        }
        if (this.mPoundString != null) {
            sb.append(" poundString=" + this.mPoundString);
        }
        if (this.mDialingNumber != null) {
            sb.append(" dialingNumber=" + this.mDialingNumber);
        }
        if (this.mPwd != null) {
            sb.append(" pwd=" + this.mPwd);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override // com.android.internal.telephony.MmiCode
    public CharSequence getUssdCode() {
        return null;
    }

    @Override // com.android.internal.telephony.MmiCode
    public String getDialString() {
        return null;
    }
}
