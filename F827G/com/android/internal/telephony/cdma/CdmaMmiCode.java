package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CdmaMmiCode extends Handler implements MmiCode {
    static final String ACTION_REGISTER = "**";
    static final int EVENT_SET_COMPLETE = 1;
    static final String LOG_TAG = "CdmaMmiCode";
    static final int MATCH_GROUP_ACTION = 2;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    String mAction;
    Context mContext;
    String mDialingNumber;
    CharSequence mMessage;
    CDMAPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    State mState = State.PENDING;
    UiccCardApplication mUiccApplication;

    CdmaMmiCode(CDMAPhone cDMAPhone, UiccCardApplication uiccCardApplication) {
        super(cDMAPhone.getHandler().getLooper());
        this.mPhone = cDMAPhone;
        this.mContext = cDMAPhone.getContext();
        this.mUiccApplication = uiccCardApplication;
    }

    private CharSequence getScString() {
        return (this.mSc == null || !isPinPukCommand()) ? "" : this.mContext.getText(17039573);
    }

    private void handlePasswordError(int i) {
        this.mState = State.FAILED;
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        stringBuilder.append(this.mContext.getText(i));
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    private static String makeEmptyNull(String str) {
        return (str == null || str.length() != 0) ? str : null;
    }

    public static CdmaMmiCode newFromDialString(String str, CDMAPhone cDMAPhone, UiccCardApplication uiccCardApplication) {
        Matcher matcher = sPatternSuppService.matcher(str);
        if (!matcher.matches()) {
            return null;
        }
        CdmaMmiCode cdmaMmiCode = new CdmaMmiCode(cDMAPhone, uiccCardApplication);
        cdmaMmiCode.mPoundString = makeEmptyNull(matcher.group(1));
        cdmaMmiCode.mAction = makeEmptyNull(matcher.group(2));
        cdmaMmiCode.mSc = makeEmptyNull(matcher.group(3));
        cdmaMmiCode.mSia = makeEmptyNull(matcher.group(5));
        cdmaMmiCode.mSib = makeEmptyNull(matcher.group(7));
        cdmaMmiCode.mSic = makeEmptyNull(matcher.group(9));
        cdmaMmiCode.mPwd = makeEmptyNull(matcher.group(11));
        cdmaMmiCode.mDialingNumber = makeEmptyNull(matcher.group(12));
        return cdmaMmiCode;
    }

    private void onSetComplete(Message message, AsyncResult asyncResult) {
        StringBuilder stringBuilder = new StringBuilder(getScString());
        stringBuilder.append("\n");
        if (asyncResult.exception != null) {
            this.mState = State.FAILED;
            if (asyncResult.exception instanceof CommandException) {
                Error commandError = ((CommandException) asyncResult.exception).getCommandError();
                if (commandError == Error.PASSWORD_INCORRECT) {
                    if (isPinPukCommand()) {
                        if (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) {
                            stringBuilder.append(this.mContext.getText(17039556));
                        } else {
                            stringBuilder.append(this.mContext.getText(17039555));
                        }
                        int i = message.arg1;
                        if (i <= 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: PUK locked, cancel as lock screen will handle this");
                            this.mState = State.CANCELLED;
                        } else if (i > 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: attemptsRemaining=" + i);
                            stringBuilder.append(this.mContext.getResources().getQuantityString(18087936, i, new Object[]{Integer.valueOf(i)}));
                        }
                    } else {
                        stringBuilder.append(this.mContext.getText(17039553));
                    }
                } else if (commandError == Error.SIM_PUK2) {
                    stringBuilder.append(this.mContext.getText(17039555));
                    stringBuilder.append("\n");
                    stringBuilder.append(this.mContext.getText(17039561));
                } else if (commandError != Error.REQUEST_NOT_SUPPORTED) {
                    stringBuilder.append(this.mContext.getText(17039546));
                } else if (this.mSc.equals(SC_PIN)) {
                    stringBuilder.append(this.mContext.getText(17039562));
                }
            } else {
                stringBuilder.append(this.mContext.getText(17039546));
            }
        } else if (isRegister()) {
            this.mState = State.COMPLETE;
            stringBuilder.append(this.mContext.getText(17039551));
        } else {
            this.mState = State.FAILED;
            stringBuilder.append(this.mContext.getText(17039546));
        }
        this.mMessage = stringBuilder;
        this.mPhone.onMMIDone(this);
    }

    public void cancel() {
        if (this.mState != State.COMPLETE && this.mState != State.FAILED) {
            this.mState = State.CANCELLED;
            this.mPhone.onMMIDone(this);
        }
    }

    public String getDialString() {
        return null;
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
        if (message.what == 1) {
            onSetComplete(message, (AsyncResult) message.obj);
        } else {
            Rlog.e(LOG_TAG, "Unexpected reply");
        }
    }

    public boolean isCancelable() {
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    public boolean isUssdRequest() {
        Rlog.w(LOG_TAG, "isUssdRequest is not implemented in CdmaMmiCode");
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void processCode() {
        try {
            if (isPinPukCommand()) {
                String str = this.mSia;
                String str2 = this.mSib;
                int length = str2.length();
                if (!isRegister()) {
                    throw new RuntimeException("Ivalid register/action=" + this.mAction);
                } else if (!str2.equals(this.mSic)) {
                    handlePasswordError(17039557);
                } else if (length < 4 || length > 8) {
                    handlePasswordError(17039558);
                } else if (this.mSc.equals(SC_PIN) && this.mUiccApplication != null && this.mUiccApplication.getState() == AppState.APPSTATE_PUK) {
                    handlePasswordError(17039560);
                } else if (this.mUiccApplication != null) {
                    Rlog.d(LOG_TAG, "process mmi service code using UiccApp sc=" + this.mSc);
                    if (this.mSc.equals(SC_PIN)) {
                        this.mUiccApplication.changeIccLockPassword(str, str2, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PIN2)) {
                        this.mUiccApplication.changeIccFdnPassword(str, str2, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PUK)) {
                        this.mUiccApplication.supplyPuk(str, str2, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PUK2)) {
                        this.mUiccApplication.supplyPuk2(str, str2, obtainMessage(1, this));
                    } else {
                        throw new RuntimeException("Unsupported service code=" + this.mSc);
                    }
                } else {
                    throw new RuntimeException("No application mUiccApplicaiton is null");
                }
            }
        } catch (RuntimeException e) {
            this.mState = State.FAILED;
            this.mMessage = this.mContext.getText(17039546);
            this.mPhone.onMMIDone(this);
        }
    }
}
