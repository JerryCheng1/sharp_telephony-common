package com.android.internal.telephony.cat;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CatResponseMessage {
    CommandDetails mCmdDet;
    ResultCode mResCode = ResultCode.OK;
    int mUsersMenuSelection = 0;
    String mUsersInput = null;
    boolean mUsersYesNoSelection = false;
    boolean mUsersConfirm = false;
    boolean mIncludeAdditionalInfo = false;
    int mAdditionalInfo = 0;
    int mEventValue = -1;
    byte[] mAddedInfo = null;

    public CatResponseMessage(CatCmdMessage cmdMsg) {
        this.mCmdDet = null;
        this.mCmdDet = cmdMsg.mCmdDet;
    }

    public void setResultCode(ResultCode resCode) {
        this.mResCode = resCode;
    }

    public void setMenuSelection(int selection) {
        this.mUsersMenuSelection = selection;
    }

    public void setInput(String input) {
        this.mUsersInput = input;
    }

    public void setEventDownload(int event, byte[] addedInfo) {
        this.mEventValue = event;
        this.mAddedInfo = addedInfo;
    }

    public void setYesNo(boolean yesNo) {
        this.mUsersYesNoSelection = yesNo;
    }

    public void setConfirmation(boolean confirm) {
        this.mUsersConfirm = confirm;
    }

    public void setAdditionalInfo(int info) {
        this.mIncludeAdditionalInfo = true;
        this.mAdditionalInfo = info;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CommandDetails getCmdDetails() {
        return this.mCmdDet;
    }
}
