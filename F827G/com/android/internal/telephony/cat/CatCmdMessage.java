package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import com.android.internal.telephony.cat.AppInterface.CommandType;

public class CatCmdMessage implements Parcelable {
    public static final Creator<CatCmdMessage> CREATOR = new Creator<CatCmdMessage>() {
        public CatCmdMessage createFromParcel(Parcel parcel) {
            return new CatCmdMessage(parcel);
        }

        public CatCmdMessage[] newArray(int i) {
            return new CatCmdMessage[i];
        }
    };
    static final int REFRESH_NAA_INIT = 3;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE = 2;
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE = 0;
    static final int REFRESH_UICC_RESET = 4;
    private BrowserSettings mBrowserSettings = null;
    private CallSettings mCallSettings = null;
    CommandDetails mCmdDet;
    private Input mInput;
    private boolean mLoadIconFailed = false;
    private Menu mMenu;
    private SetupEventListSettings mSetupEventListSettings = null;
    private TextMessage mTextMsg;
    private ToneSettings mToneSettings = null;

    public class BrowserSettings {
        public LaunchBrowserMode mode;
        public String url;
    }

    public final class BrowserTerminationCauses {
        public static final int ERROR_TERMINATION = 1;
        public static final int USER_TERMINATION = 0;
    }

    public class CallSettings {
        public TextMessage callMsg;
        public TextMessage confirmMsg;
    }

    public final class SetupEventListConstants {
        public static final int BROWSER_TERMINATION_EVENT = 8;
        public static final int BROWSING_STATUS_EVENT = 15;
        public static final int HCI_CONNECTIVITY_EVENT = 19;
        public static final int IDLE_SCREEN_AVAILABLE_EVENT = 5;
        public static final int LANGUAGE_SELECTION_EVENT = 7;
        public static final int USER_ACTIVITY_EVENT = 4;
    }

    public class SetupEventListSettings {
        public int[] eventList;
    }

    public CatCmdMessage(Parcel parcel) {
        int i = 0;
        this.mCmdDet = (CommandDetails) parcel.readParcelable(null);
        this.mTextMsg = (TextMessage) parcel.readParcelable(null);
        this.mMenu = (Menu) parcel.readParcelable(null);
        this.mInput = (Input) parcel.readParcelable(null);
        this.mLoadIconFailed = parcel.readByte() == (byte) 1;
        switch (getCmdType()) {
            case LAUNCH_BROWSER:
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = parcel.readString();
                this.mBrowserSettings.mode = LaunchBrowserMode.values()[parcel.readInt()];
                return;
            case PLAY_TONE:
                this.mToneSettings = (ToneSettings) parcel.readParcelable(null);
                return;
            case SET_UP_CALL:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = (TextMessage) parcel.readParcelable(null);
                this.mCallSettings.callMsg = (TextMessage) parcel.readParcelable(null);
                return;
            case SET_UP_EVENT_LIST:
                this.mSetupEventListSettings = new SetupEventListSettings();
                int readInt = parcel.readInt();
                this.mSetupEventListSettings.eventList = new int[readInt];
                while (i < readInt) {
                    this.mSetupEventListSettings.eventList[i] = parcel.readInt();
                    i++;
                }
                return;
            default:
                return;
        }
    }

    CatCmdMessage(CommandParams commandParams) {
        this.mCmdDet = commandParams.mCmdDet;
        this.mLoadIconFailed = commandParams.mLoadIconFailed;
        switch (getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                this.mMenu = ((SelectItemParams) commandParams).mMenu;
                return;
            case DISPLAY_TEXT:
            case SET_UP_IDLE_MODE_TEXT:
            case SEND_DTMF:
            case SEND_SMS:
            case REFRESH:
            case SEND_SS:
            case SEND_USSD:
                this.mTextMsg = ((DisplayTextParams) commandParams).mTextMsg;
                return;
            case GET_INPUT:
            case GET_INKEY:
                this.mInput = ((GetInputParams) commandParams).mInput;
                return;
            case LAUNCH_BROWSER:
                this.mTextMsg = ((LaunchBrowserParams) commandParams).mConfirmMsg;
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = ((LaunchBrowserParams) commandParams).mUrl;
                this.mBrowserSettings.mode = ((LaunchBrowserParams) commandParams).mMode;
                return;
            case PLAY_TONE:
                PlayToneParams playToneParams = (PlayToneParams) commandParams;
                this.mToneSettings = playToneParams.mSettings;
                this.mTextMsg = playToneParams.mTextMsg;
                return;
            case GET_CHANNEL_STATUS:
                this.mTextMsg = ((CallSetupParams) commandParams).mConfirmMsg;
                return;
            case SET_UP_CALL:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = ((CallSetupParams) commandParams).mConfirmMsg;
                this.mCallSettings.callMsg = ((CallSetupParams) commandParams).mCallMsg;
                return;
            case OPEN_CHANNEL:
            case CLOSE_CHANNEL:
            case RECEIVE_DATA:
            case SEND_DATA:
                this.mTextMsg = ((BIPClientParams) commandParams).mTextMsg;
                return;
            case SET_UP_EVENT_LIST:
                this.mSetupEventListSettings = new SetupEventListSettings();
                this.mSetupEventListSettings.eventList = ((SetEventListParams) commandParams).mEventInfo;
                return;
            default:
                return;
        }
    }

    public int describeContents() {
        return 0;
    }

    public Input geInput() {
        return this.mInput;
    }

    public TextMessage geTextMessage() {
        return this.mTextMsg;
    }

    public BrowserSettings getBrowserSettings() {
        return this.mBrowserSettings;
    }

    public CallSettings getCallSettings() {
        return this.mCallSettings;
    }

    public CommandType getCmdType() {
        return CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return this.mMenu;
    }

    public SetupEventListSettings getSetEventList() {
        return this.mSetupEventListSettings;
    }

    public ToneSettings getToneSettings() {
        return this.mToneSettings;
    }

    public boolean hasIconLoadFailed() {
        return this.mLoadIconFailed;
    }

    public boolean isRefreshResetOrInit() {
        return this.mCmdDet.commandQualifier == 0 || this.mCmdDet.commandQualifier == 2 || this.mCmdDet.commandQualifier == 3 || this.mCmdDet.commandQualifier == 4;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(this.mCmdDet, 0);
        parcel.writeParcelable(this.mTextMsg, 0);
        parcel.writeParcelable(this.mMenu, 0);
        parcel.writeParcelable(this.mInput, 0);
        parcel.writeByte((byte) (this.mLoadIconFailed ? 1 : 0));
        switch (getCmdType()) {
            case LAUNCH_BROWSER:
                parcel.writeString(this.mBrowserSettings.url);
                parcel.writeInt(this.mBrowserSettings.mode.ordinal());
                return;
            case PLAY_TONE:
                parcel.writeParcelable(this.mToneSettings, 0);
                return;
            case SET_UP_CALL:
                parcel.writeParcelable(this.mCallSettings.confirmMsg, 0);
                parcel.writeParcelable(this.mCallSettings.callMsg, 0);
                return;
            case SET_UP_EVENT_LIST:
                parcel.writeIntArray(this.mSetupEventListSettings.eventList);
                return;
            default:
                return;
        }
    }
}
