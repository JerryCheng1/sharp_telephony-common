package com.android.internal.telephony.cat;

public class CatResponseMessage {
    byte[] mAddedInfo = null;
    int mAdditionalInfo = 0;
    CommandDetails mCmdDet = null;
    int mEventValue = -1;
    boolean mIncludeAdditionalInfo = false;
    ResultCode mResCode = ResultCode.OK;
    boolean mUsersConfirm = false;
    String mUsersInput = null;
    int mUsersMenuSelection = 0;
    boolean mUsersYesNoSelection = false;

    public CatResponseMessage(CatCmdMessage catCmdMessage) {
        this.mCmdDet = catCmdMessage.mCmdDet;
    }

    /* Access modifiers changed, original: 0000 */
    public CommandDetails getCmdDetails() {
        return this.mCmdDet;
    }

    public void setAdditionalInfo(int i) {
        this.mIncludeAdditionalInfo = true;
        this.mAdditionalInfo = i;
    }

    public void setConfirmation(boolean z) {
        this.mUsersConfirm = z;
    }

    public void setEventDownload(int i, byte[] bArr) {
        this.mEventValue = i;
        this.mAddedInfo = bArr;
    }

    public void setInput(String str) {
        this.mUsersInput = str;
    }

    public void setMenuSelection(int i) {
        this.mUsersMenuSelection = i;
    }

    public void setResultCode(ResultCode resultCode) {
        this.mResCode = resultCode;
    }

    public void setYesNo(boolean z) {
        this.mUsersYesNoSelection = z;
    }
}
