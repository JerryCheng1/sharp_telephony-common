package com.android.internal.telephony;

import android.telephony.Rlog;
import com.android.internal.telephony.uicc.IccRecords;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CommandException extends RuntimeException {
    private Error mError;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum Error {
        INVALID_RESPONSE,
        RADIO_NOT_AVAILABLE,
        GENERIC_FAILURE,
        PASSWORD_INCORRECT,
        SIM_PIN2,
        SIM_PUK2,
        REQUEST_NOT_SUPPORTED,
        OP_NOT_ALLOWED_DURING_VOICE_CALL,
        OP_NOT_ALLOWED_BEFORE_REG_NW,
        SMS_FAIL_RETRY,
        SIM_ABSENT,
        SUBSCRIPTION_NOT_AVAILABLE,
        MODE_NOT_SUPPORTED,
        FDN_CHECK_FAILURE,
        ILLEGAL_SIM_OR_ME,
        MISSING_RESOURCE,
        NO_SUCH_ELEMENT,
        INVALID_PARAMETER,
        RETURN_NULL,
        SUBSCRIPTION_NOT_SUPPORTED,
        DIAL_MODIFIED_TO_USSD,
        DIAL_MODIFIED_TO_SS,
        DIAL_MODIFIED_TO_DIAL,
        USSD_MODIFIED_TO_DIAL,
        USSD_MODIFIED_TO_SS,
        USSD_MODIFIED_TO_USSD,
        SS_MODIFIED_TO_DIAL,
        SS_MODIFIED_TO_USSD,
        SS_MODIFIED_TO_SS,
        CBS_REQUEST_FAIL_RETRY,
        SS_ERROR_STATUS
    }

    public CommandException(Error e) {
        super(e.toString());
        this.mError = e;
    }

    public CommandException(Error e, String errString) {
        super(errString);
        this.mError = e;
    }

    public static CommandException fromRilErrno(int ril_errno) {
        switch (ril_errno) {
            case -1:
                return new CommandException(Error.INVALID_RESPONSE);
            case 0:
                return null;
            case 1:
                return new CommandException(Error.RADIO_NOT_AVAILABLE);
            case 2:
                return new CommandException(Error.GENERIC_FAILURE);
            case 3:
                return new CommandException(Error.PASSWORD_INCORRECT);
            case 4:
                return new CommandException(Error.SIM_PIN2);
            case 5:
                return new CommandException(Error.SIM_PUK2);
            case 6:
                return new CommandException(Error.REQUEST_NOT_SUPPORTED);
            case 7:
            case IccRecords.EVENT_REFRESH_OEM /* 29 */:
            default:
                Rlog.e("GSM", "Unrecognized RIL errno " + ril_errno);
                return new CommandException(Error.INVALID_RESPONSE);
            case 8:
                return new CommandException(Error.OP_NOT_ALLOWED_DURING_VOICE_CALL);
            case 9:
                return new CommandException(Error.OP_NOT_ALLOWED_BEFORE_REG_NW);
            case 10:
                return new CommandException(Error.SMS_FAIL_RETRY);
            case 11:
                return new CommandException(Error.SIM_ABSENT);
            case 12:
                return new CommandException(Error.SUBSCRIPTION_NOT_AVAILABLE);
            case 13:
                return new CommandException(Error.MODE_NOT_SUPPORTED);
            case 14:
                return new CommandException(Error.FDN_CHECK_FAILURE);
            case 15:
                return new CommandException(Error.ILLEGAL_SIM_OR_ME);
            case 16:
                return new CommandException(Error.MISSING_RESOURCE);
            case 17:
                return new CommandException(Error.NO_SUCH_ELEMENT);
            case 18:
                return new CommandException(Error.DIAL_MODIFIED_TO_USSD);
            case 19:
                return new CommandException(Error.DIAL_MODIFIED_TO_SS);
            case 20:
                return new CommandException(Error.DIAL_MODIFIED_TO_DIAL);
            case 21:
                return new CommandException(Error.USSD_MODIFIED_TO_DIAL);
            case 22:
                return new CommandException(Error.USSD_MODIFIED_TO_SS);
            case 23:
                return new CommandException(Error.USSD_MODIFIED_TO_USSD);
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                return new CommandException(Error.SS_MODIFIED_TO_DIAL);
            case 25:
                return new CommandException(Error.SS_MODIFIED_TO_USSD);
            case 26:
                return new CommandException(Error.SUBSCRIPTION_NOT_SUPPORTED);
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /* 27 */:
                return new CommandException(Error.SS_MODIFIED_TO_SS);
            case 28:
                return new CommandException(Error.INVALID_PARAMETER);
            case 30:
                if (TelBrand.IS_DCM) {
                    return new CommandException(Error.CBS_REQUEST_FAIL_RETRY);
                }
                return new CommandException(Error.INVALID_RESPONSE);
            case 31:
                if (TelBrand.IS_DCM) {
                    return new CommandException(Error.SS_ERROR_STATUS);
                }
                return new CommandException(Error.INVALID_RESPONSE);
            case 32:
                return new CommandException(Error.RETURN_NULL);
        }
    }

    public Error getCommandError() {
        return this.mError;
    }
}
