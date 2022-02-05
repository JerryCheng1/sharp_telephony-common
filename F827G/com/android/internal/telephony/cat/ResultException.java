package com.android.internal.telephony.cat;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ResultException extends CatException {
    private int mAdditionalInfo;
    private String mExplanation;
    private ResultCode mResult;

    public ResultException(ResultCode result) {
        switch (result) {
            case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:
            case NETWORK_CRNTLY_UNABLE_TO_PROCESS:
            case LAUNCH_BROWSER_ERROR:
            case MULTI_CARDS_CMD_ERROR:
            case USIM_CALL_CONTROL_PERMANENT:
            case BIP_ERROR:
            case FRAMES_ERROR:
            case MMS_ERROR:
                throw new AssertionError("For result code, " + result + ", additional information must be given!");
            default:
                this.mResult = result;
                this.mAdditionalInfo = -1;
                this.mExplanation = "";
                return;
        }
    }

    public ResultException(ResultCode result, String explanation) {
        this(result);
        this.mExplanation = explanation;
    }

    public ResultException(ResultCode result, int additionalInfo) {
        this(result);
        if (additionalInfo < 0) {
            throw new AssertionError("Additional info must be greater than zero!");
        }
        this.mAdditionalInfo = additionalInfo;
    }

    public ResultException(ResultCode result, int additionalInfo, String explanation) {
        this(result, additionalInfo);
        this.mExplanation = explanation;
    }

    public ResultCode result() {
        return this.mResult;
    }

    public boolean hasAdditionalInfo() {
        return this.mAdditionalInfo >= 0;
    }

    public int additionalInfo() {
        return this.mAdditionalInfo;
    }

    public String explanation() {
        return this.mExplanation;
    }

    @Override // java.lang.Throwable
    public String toString() {
        return "result=" + this.mResult + " additionalInfo=" + this.mAdditionalInfo + " explantion=" + this.mExplanation;
    }
}
