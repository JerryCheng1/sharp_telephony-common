package com.android.internal.telephony.cat;

public class ResultException extends CatException {
    private int mAdditionalInfo;
    private String mExplanation;
    private ResultCode mResult;

    public ResultException(ResultCode resultCode) {
        switch (resultCode) {
            case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:
            case NETWORK_CRNTLY_UNABLE_TO_PROCESS:
            case LAUNCH_BROWSER_ERROR:
            case MULTI_CARDS_CMD_ERROR:
            case USIM_CALL_CONTROL_PERMANENT:
            case BIP_ERROR:
            case FRAMES_ERROR:
            case MMS_ERROR:
                throw new AssertionError("For result code, " + resultCode + ", additional information must be given!");
            default:
                this.mResult = resultCode;
                this.mAdditionalInfo = -1;
                this.mExplanation = "";
                return;
        }
    }

    public ResultException(ResultCode resultCode, int i) {
        this(resultCode);
        if (i < 0) {
            throw new AssertionError("Additional info must be greater than zero!");
        }
        this.mAdditionalInfo = i;
    }

    public ResultException(ResultCode resultCode, int i, String str) {
        this(resultCode, i);
        this.mExplanation = str;
    }

    public ResultException(ResultCode resultCode, String str) {
        this(resultCode);
        this.mExplanation = str;
    }

    public int additionalInfo() {
        return this.mAdditionalInfo;
    }

    public String explanation() {
        return this.mExplanation;
    }

    public boolean hasAdditionalInfo() {
        return this.mAdditionalInfo >= 0;
    }

    public ResultCode result() {
        return this.mResult;
    }

    public String toString() {
        return "result=" + this.mResult + " additionalInfo=" + this.mAdditionalInfo + " explantion=" + this.mExplanation;
    }
}
