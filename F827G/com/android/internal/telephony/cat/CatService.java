package com.android.internal.telephony.cat;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

public class CatService extends Handler implements AppInterface {
    private static final boolean DBG = false;
    private static final int DEV_ID_DISPLAY = 2;
    private static final int DEV_ID_KEYPAD = 1;
    private static final int DEV_ID_NETWORK = 131;
    private static final int DEV_ID_TERMINAL = 130;
    private static final int DEV_ID_UICC = 129;
    static final int MSG_ID_ALPHA_NOTIFY = 8;
    static final int MSG_ID_CALL_SETUP = 4;
    static final int MSG_ID_EVENT_NOTIFY = 3;
    static final int MSG_ID_ICC_CHANGED = 7;
    private static final int MSG_ID_ICC_REFRESH = 30;
    static final int MSG_ID_PROACTIVE_COMMAND = 2;
    static final int MSG_ID_REFRESH = 5;
    static final int MSG_ID_RESPONSE = 6;
    static final int MSG_ID_RIL_MSG_DECODED = 9;
    static final int MSG_ID_SESSION_END = 1;
    static final String STK_DEFAULT = "Default Message";
    private CardState mCardState = CardState.CARDSTATE_ABSENT;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private CatCmdMessage mCurrntCmd = null;
    private HandlerThread mHandlerThread;
    private CatCmdMessage mMenuCmd = null;
    private RilMessageDecoder mMsgDecoder = null;
    private int mSlotId;
    private boolean mStkAppInstalled = false;
    private UiccController mUiccController;

    CatService(CommandsInterface commandsInterface, Context context, IccFileHandler iccFileHandler, int i) {
        if (commandsInterface == null || context == null || iccFileHandler == null) {
            throw new NullPointerException("Service: Input parameters must not be null");
        }
        this.mCmdIf = commandsInterface;
        this.mContext = context;
        this.mSlotId = i;
        this.mHandlerThread = new HandlerThread("Cat Telephony service" + i);
        this.mHandlerThread.start();
        this.mMsgDecoder = RilMessageDecoder.getInstance(this, iccFileHandler, i);
        if (this.mMsgDecoder == null) {
            CatLog.d((Object) this, "Null RilMessageDecoder instance");
            return;
        }
        this.mMsgDecoder.start();
        this.mCmdIf.setOnCatSessionEnd(this, 1, null);
        this.mCmdIf.setOnCatProactiveCmd(this, 2, null);
        this.mCmdIf.setOnCatEvent(this, 3, null);
        this.mCmdIf.setOnCatCallSetUp(this, 4, null);
        this.mCmdIf.registerForIccRefresh(this, 30, null);
        this.mCmdIf.setOnCatCcAlphaNotify(this, 8, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 7, null);
        this.mStkAppInstalled = isStkAppInstalled();
        CatLog.d((Object) this, "Running CAT service on Slotid: " + this.mSlotId + ". STK app installed:" + this.mStkAppInstalled);
    }

    private void broadcastAlphaMessage(String str) {
        CatLog.d((Object) this, "Broadcasting CAT Alpha message from card: " + str);
        Intent intent = new Intent(AppInterface.CAT_ALPHA_NOTIFY_ACTION);
        intent.addFlags(268435456);
        intent.putExtra(AppInterface.ALPHA_STRING, str);
        intent.putExtra("SLOT_ID", this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void broadcastCardStateAndIccRefreshResp(CardState cardState, IccRefreshResponse iccRefreshResponse) {
        Intent intent = new Intent(AppInterface.CAT_ICC_STATUS_CHANGE);
        intent.addFlags(268435456);
        boolean z = cardState == CardState.CARDSTATE_PRESENT;
        if (iccRefreshResponse != null) {
            intent.putExtra(AppInterface.REFRESH_RESULT, iccRefreshResponse.refreshResult);
            CatLog.d((Object) this, "Sending IccResult with Result: " + iccRefreshResponse.refreshResult);
        }
        intent.putExtra(AppInterface.CARD_STATUS, z);
        intent.putExtra("SLOT_ID", this.mSlotId);
        CatLog.d((Object) this, "Sending Card Status: " + cardState + " " + "cardPresent: " + z);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void broadcastCatCmdIntent(CatCmdMessage catCmdMessage) {
        Intent intent = new Intent(AppInterface.CAT_CMD_ACTION);
        intent.addFlags(268435456);
        intent.putExtra("STK CMD", catCmdMessage);
        intent.putExtra("SLOT_ID", this.mSlotId);
        CatLog.d((Object) this, "Sending CmdMsg: " + catCmdMessage + " on slotid:" + this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void encodeOptionalTags(CommandDetails commandDetails, ResultCode resultCode, Input input, ByteArrayOutputStream byteArrayOutputStream) {
        CommandType fromInt = CommandType.fromInt(commandDetails.typeOfCommand);
        if (fromInt != null) {
            switch (fromInt) {
                case PROVIDE_LOCAL_INFORMATION:
                    if (commandDetails.commandQualifier == 4 && resultCode.value() == ResultCode.OK.value()) {
                        getPliResponse(byteArrayOutputStream);
                        return;
                    }
                    return;
                case GET_INKEY:
                    if (resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value() && input != null && input.duration != null) {
                        getInKeyResponse(byteArrayOutputStream, input);
                        return;
                    }
                    return;
                default:
                    CatLog.d((Object) this, "encodeOptionalTags() Unsupported Cmd details=" + commandDetails);
                    return;
            }
        }
        CatLog.d((Object) this, "encodeOptionalTags() bad Cmd details=" + commandDetails);
    }

    private void eventDownload(int i, int i2, int i3, byte[] bArr, boolean z) {
        int i4 = 0;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(BerTlv.BER_EVENT_DOWNLOAD_TAG);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(ComprehensionTlvTag.EVENT_LIST.value() | 128);
        byteArrayOutputStream.write(1);
        byteArrayOutputStream.write(i);
        byteArrayOutputStream.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        byteArrayOutputStream.write(2);
        byteArrayOutputStream.write(i2);
        byteArrayOutputStream.write(i3);
        switch (i) {
            case 5:
                CatLog.d((Object) this, " Sending Idle Screen Available event download to ICC");
                break;
            case 7:
                CatLog.d((Object) this, " Sending Language Selection event download to ICC");
                byteArrayOutputStream.write(ComprehensionTlvTag.LANGUAGE.value() | 128);
                byteArrayOutputStream.write(2);
                break;
            case 19:
                CatLog.d((Object) this, " Sending HCI Connectivity event download to ICC");
                break;
        }
        if (bArr != null) {
            int length = bArr.length;
            while (i4 < length) {
                byteArrayOutputStream.write(bArr[i4]);
                i4++;
            }
        }
        byte[] toByteArray = byteArrayOutputStream.toByteArray();
        toByteArray[1] = (byte) (toByteArray.length - 2);
        String bytesToHexString = IccUtils.bytesToHexString(toByteArray);
        CatLog.d((Object) this, "ENVELOPE COMMAND: " + bytesToHexString);
        this.mCmdIf.sendEnvelope(bytesToHexString, null);
    }

    private void getInKeyResponse(ByteArrayOutputStream byteArrayOutputStream, Input input) {
        byteArrayOutputStream.write(ComprehensionTlvTag.DURATION.value());
        byteArrayOutputStream.write(2);
        TimeUnit timeUnit = input.duration.timeUnit;
        byteArrayOutputStream.write(TimeUnit.SECOND.value());
        byteArrayOutputStream.write(input.duration.timeInterval);
    }

    private void getPliResponse(ByteArrayOutputStream byteArrayOutputStream) {
        String str = SystemProperties.get("persist.sys.language");
        if (str != null) {
            byteArrayOutputStream.write(ComprehensionTlvTag.LANGUAGE.value());
            ResponseData.writeLength(byteArrayOutputStream, str.length());
            byteArrayOutputStream.write(str.getBytes(), 0, str.length());
        }
    }

    /* JADX WARNING: Missing block: B:6:0x002d, code skipped:
            switch(r4) {
                case com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_MENU :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x003e;
                case com.android.internal.telephony.cat.AppInterface.CommandType.DISPLAY_TEXT :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0074;
                case com.android.internal.telephony.cat.AppInterface.CommandType.REFRESH :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_IDLE_MODE_TEXT :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_EVENT_LIST :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0092;
                case com.android.internal.telephony.cat.AppInterface.CommandType.PROVIDE_LOCAL_INFORMATION :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.LAUNCH_BROWSER :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0085;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SELECT_ITEM :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x004c;
                case com.android.internal.telephony.cat.AppInterface.CommandType.GET_INPUT :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0054;
                case com.android.internal.telephony.cat.AppInterface.CommandType.GET_INKEY :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0054;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SEND_DTMF :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SEND_SMS :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SEND_SS :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SEND_USSD :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.PLAY_TONE :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0030;
                case com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_CALL :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0087;
                case com.android.internal.telephony.cat.AppInterface.CommandType.OPEN_CHANNEL :com.android.internal.telephony.cat.AppInterface$CommandType: goto L_0x0087;
                default: goto L_0x0030;
            };
     */
    /* JADX WARNING: Missing block: B:7:0x0030, code skipped:
            r5 = null;
     */
    /* JADX WARNING: Missing block: B:8:0x0031, code skipped:
            sendTerminalResponse(r1, r9.mResCode, r9.mIncludeAdditionalInfo, r9.mAdditionalInfo, r5);
            r8.mCurrntCmd = null;
     */
    /* JADX WARNING: Missing block: B:10:0x0042, code skipped:
            if (r9.mResCode != com.android.internal.telephony.cat.ResultCode.HELP_INFO_REQUIRED) goto L_0x004a;
     */
    /* JADX WARNING: Missing block: B:11:0x0044, code skipped:
            sendMenuSelection(r9.mUsersMenuSelection, r2);
     */
    /* JADX WARNING: Missing block: B:12:0x004a, code skipped:
            r2 = false;
     */
    /* JADX WARNING: Missing block: B:13:0x004c, code skipped:
            r5 = new com.android.internal.telephony.cat.SelectItemResponseData(r9.mUsersMenuSelection);
     */
    /* JADX WARNING: Missing block: B:14:0x0054, code skipped:
            r2 = r8.mCurrntCmd.geInput();
     */
    /* JADX WARNING: Missing block: B:15:0x005c, code skipped:
            if (r2.yesNo != false) goto L_0x006c;
     */
    /* JADX WARNING: Missing block: B:16:0x005e, code skipped:
            if (r0 != false) goto L_0x0030;
     */
    /* JADX WARNING: Missing block: B:17:0x0060, code skipped:
            r5 = new com.android.internal.telephony.cat.GetInkeyInputResponseData(r9.mUsersInput, r2.ucs2, r2.packed);
     */
    /* JADX WARNING: Missing block: B:18:0x006c, code skipped:
            r5 = new com.android.internal.telephony.cat.GetInkeyInputResponseData(r9.mUsersYesNoSelection);
     */
    /* JADX WARNING: Missing block: B:20:0x0078, code skipped:
            if (r9.mResCode != com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS) goto L_0x007f;
     */
    /* JADX WARNING: Missing block: B:21:0x007a, code skipped:
            r9.setAdditionalInfo(1);
            r5 = null;
     */
    /* JADX WARNING: Missing block: B:22:0x007f, code skipped:
            r9.mIncludeAdditionalInfo = false;
            r9.mAdditionalInfo = 0;
            r5 = null;
     */
    /* JADX WARNING: Missing block: B:23:0x0085, code skipped:
            r5 = null;
     */
    /* JADX WARNING: Missing block: B:24:0x0087, code skipped:
            r8.mCmdIf.handleCallSetupRequestFromSim(r9.mUsersConfirm, null);
            r8.mCurrntCmd = null;
     */
    /* JADX WARNING: Missing block: B:26:0x0095, code skipped:
            if (5 != r9.mEventValue) goto L_0x00a2;
     */
    /* JADX WARNING: Missing block: B:27:0x0097, code skipped:
            eventDownload(r9.mEventValue, 2, 129, r9.mAddedInfo, false);
     */
    /* JADX WARNING: Missing block: B:28:0x00a2, code skipped:
            eventDownload(r9.mEventValue, 130, 129, r9.mAddedInfo, false);
     */
    /* JADX WARNING: Missing block: B:38:0x00ca, code skipped:
            r5 = null;
     */
    /* JADX WARNING: Missing block: B:41:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:42:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:43:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:44:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:45:?, code skipped:
            return;
     */
    private void handleCmdResponse(com.android.internal.telephony.cat.CatResponseMessage r9) {
        /*
        r8 = this;
        r3 = 129; // 0x81 float:1.81E-43 double:6.37E-322;
        r2 = 1;
        r5 = 0;
        r6 = 0;
        r0 = r8.validateResponse(r9);
        if (r0 != 0) goto L_0x000c;
    L_0x000b:
        return;
    L_0x000c:
        r1 = r9.getCmdDetails();
        r0 = r1.typeOfCommand;
        r4 = com.android.internal.telephony.cat.AppInterface.CommandType.fromInt(r0);
        r0 = com.android.internal.telephony.cat.CatService.AnonymousClass1.$SwitchMap$com$android$internal$telephony$cat$ResultCode;
        r7 = r9.mResCode;
        r7 = r7.ordinal();
        r0 = r0[r7];
        switch(r0) {
            case 1: goto L_0x0024;
            case 2: goto L_0x00cd;
            case 3: goto L_0x00cd;
            case 4: goto L_0x00cd;
            case 5: goto L_0x00cd;
            case 6: goto L_0x00cd;
            case 7: goto L_0x00cd;
            case 8: goto L_0x00cd;
            case 9: goto L_0x00cd;
            case 10: goto L_0x00cd;
            case 11: goto L_0x00cd;
            case 12: goto L_0x00cd;
            case 13: goto L_0x00cd;
            case 14: goto L_0x00ae;
            case 15: goto L_0x00ae;
            case 16: goto L_0x00c2;
            case 17: goto L_0x00ca;
            default: goto L_0x0023;
        };
    L_0x0023:
        goto L_0x000b;
    L_0x0024:
        r0 = r2;
    L_0x0025:
        r7 = com.android.internal.telephony.cat.CatService.AnonymousClass1.$SwitchMap$com$android$internal$telephony$cat$AppInterface$CommandType;
        r4 = r4.ordinal();
        r4 = r7[r4];
        switch(r4) {
            case 1: goto L_0x003e;
            case 2: goto L_0x0074;
            case 3: goto L_0x0030;
            case 4: goto L_0x0030;
            case 5: goto L_0x0092;
            case 6: goto L_0x0030;
            case 7: goto L_0x0085;
            case 8: goto L_0x004c;
            case 9: goto L_0x0054;
            case 10: goto L_0x0054;
            case 11: goto L_0x0030;
            case 12: goto L_0x0030;
            case 13: goto L_0x0030;
            case 14: goto L_0x0030;
            case 15: goto L_0x0030;
            case 16: goto L_0x0087;
            case 17: goto L_0x0087;
            default: goto L_0x0030;
        };
    L_0x0030:
        r5 = r6;
    L_0x0031:
        r2 = r9.mResCode;
        r3 = r9.mIncludeAdditionalInfo;
        r4 = r9.mAdditionalInfo;
        r0 = r8;
        r0.sendTerminalResponse(r1, r2, r3, r4, r5);
        r8.mCurrntCmd = r6;
        goto L_0x000b;
    L_0x003e:
        r0 = r9.mResCode;
        r1 = com.android.internal.telephony.cat.ResultCode.HELP_INFO_REQUIRED;
        if (r0 != r1) goto L_0x004a;
    L_0x0044:
        r0 = r9.mUsersMenuSelection;
        r8.sendMenuSelection(r0, r2);
        goto L_0x000b;
    L_0x004a:
        r2 = r5;
        goto L_0x0044;
    L_0x004c:
        r5 = new com.android.internal.telephony.cat.SelectItemResponseData;
        r0 = r9.mUsersMenuSelection;
        r5.<init>(r0);
        goto L_0x0031;
    L_0x0054:
        r2 = r8.mCurrntCmd;
        r2 = r2.geInput();
        r3 = r2.yesNo;
        if (r3 != 0) goto L_0x006c;
    L_0x005e:
        if (r0 != 0) goto L_0x0030;
    L_0x0060:
        r5 = new com.android.internal.telephony.cat.GetInkeyInputResponseData;
        r0 = r9.mUsersInput;
        r3 = r2.ucs2;
        r2 = r2.packed;
        r5.<init>(r0, r3, r2);
        goto L_0x0031;
    L_0x006c:
        r5 = new com.android.internal.telephony.cat.GetInkeyInputResponseData;
        r0 = r9.mUsersYesNoSelection;
        r5.<init>(r0);
        goto L_0x0031;
    L_0x0074:
        r0 = r9.mResCode;
        r3 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        if (r0 != r3) goto L_0x007f;
    L_0x007a:
        r9.setAdditionalInfo(r2);
        r5 = r6;
        goto L_0x0031;
    L_0x007f:
        r9.mIncludeAdditionalInfo = r5;
        r9.mAdditionalInfo = r5;
        r5 = r6;
        goto L_0x0031;
    L_0x0085:
        r5 = r6;
        goto L_0x0031;
    L_0x0087:
        r0 = r8.mCmdIf;
        r1 = r9.mUsersConfirm;
        r0.handleCallSetupRequestFromSim(r1, r6);
        r8.mCurrntCmd = r6;
        goto L_0x000b;
    L_0x0092:
        r0 = 5;
        r1 = r9.mEventValue;
        if (r0 != r1) goto L_0x00a2;
    L_0x0097:
        r1 = r9.mEventValue;
        r2 = 2;
        r4 = r9.mAddedInfo;
        r0 = r8;
        r0.eventDownload(r1, r2, r3, r4, r5);
        goto L_0x000b;
    L_0x00a2:
        r1 = r9.mEventValue;
        r2 = 130; // 0x82 float:1.82E-43 double:6.4E-322;
        r4 = r9.mAddedInfo;
        r0 = r8;
        r0.eventDownload(r1, r2, r3, r4, r5);
        goto L_0x000b;
    L_0x00ae:
        r0 = com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_CALL;
        if (r4 == r0) goto L_0x00b6;
    L_0x00b2:
        r0 = com.android.internal.telephony.cat.AppInterface.CommandType.OPEN_CHANNEL;
        if (r4 != r0) goto L_0x00bf;
    L_0x00b6:
        r0 = r8.mCmdIf;
        r0.handleCallSetupRequestFromSim(r5, r6);
        r8.mCurrntCmd = r6;
        goto L_0x000b;
    L_0x00bf:
        r5 = r6;
        goto L_0x0031;
    L_0x00c2:
        r0 = com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_CALL;
        if (r4 != r0) goto L_0x00ca;
    L_0x00c6:
        r8.mCurrntCmd = r6;
        goto L_0x000b;
    L_0x00ca:
        r5 = r6;
        goto L_0x0031;
    L_0x00cd:
        r0 = r5;
        goto L_0x0025;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.CatService.handleCmdResponse(com.android.internal.telephony.cat.CatResponseMessage):void");
    }

    private void handleCommand(CommandParams commandParams, boolean z) {
        CatLog.d((Object) this, commandParams.getCommandType().name());
        CatCmdMessage catCmdMessage = new CatCmdMessage(commandParams);
        ResultCode resultCode;
        switch (commandParams.getCommandType()) {
            case SET_UP_MENU:
                if (removeMenu(catCmdMessage.getMenu())) {
                    this.mMenuCmd = null;
                } else {
                    this.mMenuCmd = catCmdMessage;
                }
                resultCode = commandParams.mLoadIconFailed ? ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK;
                if (z) {
                    sendTerminalResponse(commandParams.mCmdDet, resultCode, false, 0, null);
                    break;
                }
                break;
            case DISPLAY_TEXT:
            case SELECT_ITEM:
            case GET_INPUT:
            case GET_INKEY:
            case PLAY_TONE:
                break;
            case REFRESH:
                CatLog.d((Object) this, "Pass Refresh to Stk app");
                break;
            case SET_UP_IDLE_MODE_TEXT:
                resultCode = commandParams.mLoadIconFailed ? ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK;
                if (z) {
                    sendTerminalResponse(commandParams.mCmdDet, resultCode, false, 0, null);
                    break;
                }
                break;
            case SET_UP_EVENT_LIST:
                if (z) {
                    if (!isSupportedSetupEventCommand(catCmdMessage)) {
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                        break;
                    } else {
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, null);
                        break;
                    }
                }
                break;
            case PROVIDE_LOCAL_INFORMATION:
                switch (commandParams.mCmdDet.commandQualifier) {
                    case 3:
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, new DTTZResponseData(null));
                        return;
                    case 4:
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, new LanguageResponseData(Locale.getDefault().getLanguage()));
                        return;
                    default:
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, null);
                        return;
                }
            case LAUNCH_BROWSER:
                if (((LaunchBrowserParams) commandParams).mConfirmMsg.text != null && ((LaunchBrowserParams) commandParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                    ((LaunchBrowserParams) commandParams).mConfirmMsg.text = this.mContext.getText(17040964).toString();
                    break;
                }
            case SEND_DTMF:
            case SEND_SMS:
            case SEND_SS:
            case SEND_USSD:
                if (((DisplayTextParams) commandParams).mTextMsg.text != null && ((DisplayTextParams) commandParams).mTextMsg.text.equals(STK_DEFAULT)) {
                    ((DisplayTextParams) commandParams).mTextMsg.text = this.mContext.getText(17040963).toString();
                    break;
                }
            case SET_UP_CALL:
                if (((CallSetupParams) commandParams).mConfirmMsg.text != null && ((CallSetupParams) commandParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                    ((CallSetupParams) commandParams).mConfirmMsg.text = this.mContext.getText(17040965).toString();
                    break;
                }
            case OPEN_CHANNEL:
            case CLOSE_CHANNEL:
            case RECEIVE_DATA:
            case SEND_DATA:
                BIPClientParams bIPClientParams = (BIPClientParams) commandParams;
                boolean z2;
                try {
                    z2 = this.mContext.getResources().getBoolean(17956989);
                } catch (NotFoundException e) {
                    z2 = false;
                }
                if (bIPClientParams.mTextMsg.text != null || (!bIPClientParams.mHasAlphaId && !z2)) {
                    if (!this.mStkAppInstalled) {
                        CatLog.d((Object) this, "No STK application found.");
                        if (z) {
                            sendTerminalResponse(commandParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                            return;
                        }
                    }
                    if (z && (commandParams.getCommandType() == CommandType.CLOSE_CHANNEL || commandParams.getCommandType() == CommandType.RECEIVE_DATA || commandParams.getCommandType() == CommandType.SEND_DATA)) {
                        sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, null);
                        break;
                    }
                }
                CatLog.d((Object) this, "cmd " + commandParams.getCommandType() + " with null alpha id");
                if (z) {
                    sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                } else if (commandParams.getCommandType() == CommandType.OPEN_CHANNEL) {
                    this.mCmdIf.handleCallSetupRequestFromSim(true, null);
                    return;
                } else {
                    return;
                }
                break;
            case ACTIVATE:
                sendTerminalResponse(commandParams.mCmdDet, ResultCode.OK, false, 0, null);
                break;
            default:
                CatLog.d((Object) this, "Unsupported command");
                return;
        }
        this.mCurrntCmd = catCmdMessage;
        broadcastCatCmdIntent(catCmdMessage);
    }

    private void handleRilMsg(RilMessage rilMessage) {
        if (rilMessage != null) {
            CommandParams commandParams;
            switch (rilMessage.mId) {
                case 1:
                    handleSessionEnd();
                    return;
                case 2:
                    try {
                        commandParams = (CommandParams) rilMessage.mData;
                        if (commandParams == null) {
                            return;
                        }
                        if (rilMessage.mResCode == ResultCode.OK) {
                            handleCommand(commandParams, true);
                            return;
                        }
                        sendTerminalResponse(commandParams.mCmdDet, rilMessage.mResCode, false, 0, null);
                        return;
                    } catch (ClassCastException e) {
                        CatLog.d((Object) this, "Fail to parse proactive command");
                        if (this.mCurrntCmd != null) {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false, 0, null);
                            return;
                        }
                        return;
                    }
                case 3:
                    if (rilMessage.mResCode == ResultCode.OK) {
                        commandParams = (CommandParams) rilMessage.mData;
                        if (commandParams != null) {
                            handleCommand(commandParams, false);
                            return;
                        }
                        return;
                    }
                    return;
                case 5:
                    commandParams = (CommandParams) rilMessage.mData;
                    if (commandParams != null) {
                        handleCommand(commandParams, false);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    private void handleSessionEnd() {
        CatLog.d((Object) this, "SESSION END on " + this.mSlotId);
        this.mCurrntCmd = this.mMenuCmd;
        Intent intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
        intent.putExtra("SLOT_ID", this.mSlotId);
        intent.addFlags(268435456);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private boolean isStkAppInstalled() {
        List queryBroadcastReceivers = this.mContext.getPackageManager().queryBroadcastReceivers(new Intent(AppInterface.CAT_CMD_ACTION), 128);
        return (queryBroadcastReceivers == null ? 0 : queryBroadcastReceivers.size()) > 0;
    }

    private boolean isSupportedSetupEventCommand(CatCmdMessage catCmdMessage) {
        boolean z = true;
        for (int i : catCmdMessage.getSetEventList().eventList) {
            CatLog.d((Object) this, "Event: " + i);
            switch (i) {
                case 5:
                case 7:
                case 19:
                    break;
                default:
                    z = false;
                    break;
            }
        }
        return z;
    }

    private boolean removeMenu(Menu menu) {
        try {
            return menu.items.size() == 1 && menu.items.get(0) == null;
        } catch (NullPointerException e) {
            CatLog.d((Object) this, "Unable to get Menu's items size");
            return true;
        }
    }

    private void sendMenuSelection(int i, boolean z) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(211);
        byteArrayOutputStream.write(0);
        byteArrayOutputStream.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        byteArrayOutputStream.write(2);
        byteArrayOutputStream.write(1);
        byteArrayOutputStream.write(129);
        byteArrayOutputStream.write(ComprehensionTlvTag.ITEM_ID.value() | 128);
        byteArrayOutputStream.write(1);
        byteArrayOutputStream.write(i);
        if (z) {
            byteArrayOutputStream.write(ComprehensionTlvTag.HELP_REQUEST.value());
            byteArrayOutputStream.write(0);
        }
        byte[] toByteArray = byteArrayOutputStream.toByteArray();
        toByteArray[1] = (byte) (toByteArray.length - 2);
        this.mCmdIf.sendEnvelope(IccUtils.bytesToHexString(toByteArray), null);
    }

    private void sendTerminalResponse(CommandDetails commandDetails, ResultCode resultCode, boolean z, int i, ResponseData responseData) {
        if (commandDetails != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Input geInput = this.mCurrntCmd != null ? this.mCurrntCmd.geInput() : null;
            int value = ComprehensionTlvTag.COMMAND_DETAILS.value();
            if (commandDetails.compRequired) {
                value |= 128;
            }
            byteArrayOutputStream.write(value);
            byteArrayOutputStream.write(3);
            byteArrayOutputStream.write(commandDetails.commandNumber);
            byteArrayOutputStream.write(commandDetails.typeOfCommand);
            byteArrayOutputStream.write(commandDetails.commandQualifier);
            byteArrayOutputStream.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
            byteArrayOutputStream.write(2);
            byteArrayOutputStream.write(130);
            byteArrayOutputStream.write(129);
            value = ComprehensionTlvTag.RESULT.value();
            if (commandDetails.compRequired) {
                value |= 128;
            }
            byteArrayOutputStream.write(value);
            byteArrayOutputStream.write(z ? 2 : 1);
            byteArrayOutputStream.write(resultCode.value());
            if (z) {
                byteArrayOutputStream.write(i);
            }
            if (responseData != null) {
                responseData.format(byteArrayOutputStream);
            } else {
                encodeOptionalTags(commandDetails, resultCode, geInput, byteArrayOutputStream);
            }
            this.mCmdIf.sendTerminalResponse(IccUtils.bytesToHexString(byteArrayOutputStream.toByteArray()), null);
        }
    }

    private boolean validateResponse(CatResponseMessage catResponseMessage) {
        if (catResponseMessage.mCmdDet.typeOfCommand == CommandType.SET_UP_EVENT_LIST.value() || catResponseMessage.mCmdDet.typeOfCommand == CommandType.SET_UP_MENU.value()) {
            CatLog.d((Object) this, "CmdType: " + catResponseMessage.mCmdDet.typeOfCommand);
            return true;
        } else if (this.mCurrntCmd == null) {
            return false;
        } else {
            boolean compareTo = catResponseMessage.mCmdDet.compareTo(this.mCurrntCmd.mCmdDet);
            CatLog.d((Object) this, "isResponse for last valid cmd: " + compareTo);
            return compareTo;
        }
    }

    public void dispose() {
        CatLog.d((Object) this, "Disposing CatService object for slot: " + this.mSlotId);
        broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_ABSENT, null);
        this.mCmdIf.unSetOnCatSessionEnd(this);
        this.mCmdIf.unSetOnCatProactiveCmd(this);
        this.mCmdIf.unSetOnCatEvent(this);
        this.mCmdIf.unSetOnCatCallSetUp(this);
        this.mCmdIf.unSetOnCatCcAlphaNotify(this);
        this.mCmdIf.unregisterForIccRefresh(this);
        if (this.mUiccController != null) {
            this.mUiccController.unregisterForIccChanged(this);
            this.mUiccController = null;
        }
        if (this.mMsgDecoder != null) {
            this.mMsgDecoder.dispose(this.mSlotId);
            this.mMsgDecoder = null;
        }
        this.mHandlerThread.quit();
        this.mHandlerThread = null;
        removeCallbacksAndMessages(null);
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        CatLog.d((Object) this, "Service finalized");
    }

    public void handleMessage(Message message) {
        CatLog.d((Object) this, "handleMessage[" + message.what + "]");
        AsyncResult asyncResult;
        switch (message.what) {
            case 1:
            case 2:
            case 3:
            case 5:
                String str;
                CatLog.d((Object) this, "ril message arrived,slotid:" + this.mSlotId);
                if (message.obj != null) {
                    asyncResult = (AsyncResult) message.obj;
                    if (!(asyncResult == null || asyncResult.result == null)) {
                        try {
                            str = (String) asyncResult.result;
                            this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(message.what, str));
                            return;
                        } catch (ClassCastException e) {
                            return;
                        }
                    }
                }
                str = null;
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(message.what, str));
                return;
            case 4:
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(message.what, null));
                return;
            case 6:
                handleCmdResponse((CatResponseMessage) message.obj);
                return;
            case 7:
                CatLog.d((Object) this, "MSG_ID_ICC_CHANGED");
                updateIccAvailability();
                return;
            case 8:
                CatLog.d((Object) this, "Received CAT CC Alpha message from card");
                if (message.obj != null) {
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult == null || asyncResult.result == null) {
                        CatLog.d((Object) this, "CAT Alpha message: ar.result is null");
                        return;
                    } else {
                        broadcastAlphaMessage((String) asyncResult.result);
                        return;
                    }
                }
                CatLog.d((Object) this, "CAT Alpha message: msg.obj is null");
                return;
            case 9:
                handleRilMsg((RilMessage) message.obj);
                return;
            case 30:
                if (message.obj != null) {
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult == null || asyncResult.result == null) {
                        CatLog.d((Object) this, "Icc REFRESH with exception: " + asyncResult.exception);
                        return;
                    } else {
                        broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_PRESENT, (IccRefreshResponse) asyncResult.result);
                        return;
                    }
                }
                CatLog.d((Object) this, "IccRefresh Message is null");
                return;
            default:
                throw new AssertionError("Unrecognized CAT command: " + message.what);
        }
    }

    public void onCmdResponse(CatResponseMessage catResponseMessage) {
        synchronized (this) {
            if (catResponseMessage != null) {
                obtainMessage(6, catResponseMessage).sendToTarget();
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void updateIccAvailability() {
        if (this.mUiccController != null) {
            CardState cardState = CardState.CARDSTATE_ABSENT;
            UiccCard uiccCard = this.mUiccController.getUiccCard(this.mSlotId);
            if (uiccCard != null) {
                cardState = uiccCard.getCardState();
            }
            CardState cardState2 = this.mCardState;
            this.mCardState = cardState;
            CatLog.d((Object) this, "New Card State = " + cardState + " " + "Old Card State = " + cardState2);
            if (cardState2 == CardState.CARDSTATE_PRESENT && cardState != CardState.CARDSTATE_PRESENT) {
                broadcastCardStateAndIccRefreshResp(cardState, null);
            } else if (cardState2 != CardState.CARDSTATE_PRESENT && cardState == CardState.CARDSTATE_PRESENT) {
                this.mCmdIf.reportStkServiceIsRunning(null);
            }
        }
    }
}
