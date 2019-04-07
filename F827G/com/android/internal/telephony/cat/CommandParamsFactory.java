package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.Iterator;
import java.util.List;

class CommandParamsFactory extends Handler {
    static final int DTTZ_SETTING = 3;
    static final int LANGUAGE_SETTING = 4;
    static final int LOAD_MULTI_ICONS = 2;
    static final int LOAD_NO_ICON = 0;
    static final int LOAD_SINGLE_ICON = 1;
    private static final int MAX_GSM7_DEFAULT_CHARS = 239;
    private static final int MAX_UCS2_CHARS = 118;
    static final int MSG_ID_LOAD_ICON_DONE = 1;
    private static CommandParamsFactory sInstance = null;
    private RilMessageDecoder mCaller = null;
    private CommandParams mCmdParams = null;
    private int mIconLoadState = 0;
    private IconLoader mIconLoader;
    private boolean mloadIcon = false;

    private CommandParamsFactory(RilMessageDecoder rilMessageDecoder, IccFileHandler iccFileHandler) {
        this.mCaller = rilMessageDecoder;
        this.mIconLoader = IconLoader.getInstance(this, iccFileHandler);
    }

    static CommandParamsFactory getInstance(RilMessageDecoder rilMessageDecoder, IccFileHandler iccFileHandler) {
        CommandParamsFactory commandParamsFactory;
        synchronized (CommandParamsFactory.class) {
            try {
                commandParamsFactory = sInstance != null ? sInstance : iccFileHandler != null ? new CommandParamsFactory(rilMessageDecoder, iccFileHandler) : null;
            } catch (Throwable th) {
                Class cls = CommandParamsFactory.class;
            }
        }
        return commandParamsFactory;
    }

    private boolean processActivate(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process " + CommandType.fromInt(commandDetails.typeOfCommand).name());
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.ACTIVATE_DESCRIPTOR, list);
        if (searchForTag != null) {
            int retrieveTarget = ValueParser.retrieveTarget(searchForTag);
            this.mCmdParams = new CommandParams(commandDetails);
            CatLog.d((Object) this, "Activate cmd target = " + retrieveTarget);
        } else {
            CatLog.d((Object) this, "ctlv is null");
        }
        return false;
    }

    private boolean processBIPClient(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        boolean z;
        CommandType fromInt = CommandType.fromInt(commandDetails.typeOfCommand);
        if (fromInt != null) {
            CatLog.d((Object) this, "process " + fromInt.name());
        }
        TextMessage textMessage = new TextMessage();
        IconId iconId = null;
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.ALPHA_ID, list);
        if (searchForTag != null) {
            textMessage.text = ValueParser.retrieveAlphaId(searchForTag);
            CatLog.d((Object) this, "alpha TLV text=" + textMessage.text);
            z = true;
        } else {
            z = false;
        }
        ComprehensionTlv searchForTag2 = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag2 != null) {
            iconId = ValueParser.retrieveIconId(searchForTag2);
            textMessage.iconSelfExplanatory = iconId.selfExplanatory;
        }
        textMessage.responseNeeded = false;
        this.mCmdParams = new BIPClientParams(commandDetails, textMessage, z);
        if (iconId == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private CommandDetails processCommandDetails(List<ComprehensionTlv> list) {
        CommandDetails commandDetails = null;
        if (list == null) {
            return commandDetails;
        }
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.COMMAND_DETAILS, list);
        if (searchForTag == null) {
            return commandDetails;
        }
        try {
            return ValueParser.retrieveCommandDetails(searchForTag);
        } catch (ResultException e) {
            CatLog.d((Object) this, "processCommandDetails: Failed to procees command details e=" + e);
            return commandDetails;
        }
    }

    private boolean processDisplayText(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process DisplayText");
        TextMessage textMessage = new TextMessage();
        IconId iconId = null;
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.TEXT_STRING, list);
        if (searchForTag != null) {
            textMessage.text = ValueParser.retrieveTextString(searchForTag);
        }
        if (textMessage.text == null) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
        if (searchForTag(ComprehensionTlvTag.IMMEDIATE_RESPONSE, list) != null) {
            textMessage.responseNeeded = false;
        }
        searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag != null) {
            iconId = ValueParser.retrieveIconId(searchForTag);
            textMessage.iconSelfExplanatory = iconId.selfExplanatory;
        }
        searchForTag = searchForTag(ComprehensionTlvTag.DURATION, list);
        if (searchForTag != null) {
            textMessage.duration = ValueParser.retrieveDuration(searchForTag);
        }
        textMessage.isHighPriority = (commandDetails.commandQualifier & 1) != 0;
        textMessage.userClear = (commandDetails.commandQualifier & 128) != 0;
        this.mCmdParams = new DisplayTextParams(commandDetails, textMessage);
        if (iconId == null) {
            return false;
        }
        this.mloadIcon = true;
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processEventNotify(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process EventNotify");
        TextMessage textMessage = new TextMessage();
        IconId iconId = null;
        textMessage.text = ValueParser.retrieveAlphaId(searchForTag(ComprehensionTlvTag.ALPHA_ID, list));
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag != null) {
            iconId = ValueParser.retrieveIconId(searchForTag);
            textMessage.iconSelfExplanatory = iconId.selfExplanatory;
        }
        textMessage.responseNeeded = false;
        this.mCmdParams = new DisplayTextParams(commandDetails, textMessage);
        if (iconId == null) {
            return false;
        }
        this.mloadIcon = true;
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processGetInkey(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process GetInkey");
        Input input = new Input();
        IconId iconId = null;
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.TEXT_STRING, list);
        if (searchForTag != null) {
            input.text = ValueParser.retrieveTextString(searchForTag);
            searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
            if (searchForTag != null) {
                iconId = ValueParser.retrieveIconId(searchForTag);
            }
            searchForTag = searchForTag(ComprehensionTlvTag.DURATION, list);
            if (searchForTag != null) {
                input.duration = ValueParser.retrieveDuration(searchForTag);
            }
            input.minLen = 1;
            input.maxLen = 1;
            input.digitOnly = (commandDetails.commandQualifier & 1) == 0;
            input.ucs2 = (commandDetails.commandQualifier & 2) != 0;
            input.yesNo = (commandDetails.commandQualifier & 4) != 0;
            input.helpAvailable = (commandDetails.commandQualifier & 128) != 0;
            input.echo = true;
            this.mCmdParams = new GetInputParams(commandDetails, input);
            if (iconId == null) {
                return false;
            }
            this.mloadIcon = true;
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processGetInput(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process GetInput");
        Input input = new Input();
        IconId iconId = null;
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.TEXT_STRING, list);
        if (searchForTag != null) {
            input.text = ValueParser.retrieveTextString(searchForTag);
            searchForTag = searchForTag(ComprehensionTlvTag.RESPONSE_LENGTH, list);
            if (searchForTag != null) {
                try {
                    byte[] rawValue = searchForTag.getRawValue();
                    int valueIndex = searchForTag.getValueIndex();
                    input.minLen = rawValue[valueIndex] & 255;
                    input.maxLen = rawValue[valueIndex + 1] & 255;
                    searchForTag = searchForTag(ComprehensionTlvTag.DEFAULT_TEXT, list);
                    if (searchForTag != null) {
                        input.defaultText = ValueParser.retrieveTextString(searchForTag);
                    }
                    searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
                    if (searchForTag != null) {
                        iconId = ValueParser.retrieveIconId(searchForTag);
                    }
                    input.digitOnly = (commandDetails.commandQualifier & 1) == 0;
                    input.ucs2 = (commandDetails.commandQualifier & 2) != 0;
                    input.echo = (commandDetails.commandQualifier & 4) == 0;
                    input.packed = (commandDetails.commandQualifier & 8) != 0;
                    input.helpAvailable = (commandDetails.commandQualifier & 128) != 0;
                    if (input.ucs2 && input.maxLen > MAX_UCS2_CHARS) {
                        CatLog.d((Object) this, "UCS2: received maxLen = " + input.maxLen + ", truncating to " + MAX_UCS2_CHARS);
                        input.maxLen = MAX_UCS2_CHARS;
                    } else if (!input.packed && input.maxLen > MAX_GSM7_DEFAULT_CHARS) {
                        CatLog.d((Object) this, "GSM 7Bit Default: received maxLen = " + input.maxLen + ", truncating to " + MAX_GSM7_DEFAULT_CHARS);
                        input.maxLen = MAX_GSM7_DEFAULT_CHARS;
                    }
                    this.mCmdParams = new GetInputParams(commandDetails, input);
                    if (iconId == null) {
                        return false;
                    }
                    this.mloadIcon = true;
                    this.mIconLoadState = 1;
                    this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
                    return true;
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processLaunchBrowser(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        String gsm8BitUnpackedToString;
        LaunchBrowserMode launchBrowserMode;
        IconId iconId = null;
        CatLog.d((Object) this, "process LaunchBrowser");
        TextMessage textMessage = new TextMessage();
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.URL, list);
        if (searchForTag != null) {
            try {
                byte[] rawValue = searchForTag.getRawValue();
                int valueIndex = searchForTag.getValueIndex();
                int length = searchForTag.getLength();
                gsm8BitUnpackedToString = length > 0 ? GsmAlphabet.gsm8BitUnpackedToString(rawValue, valueIndex, length) : null;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        gsm8BitUnpackedToString = null;
        textMessage.text = ValueParser.retrieveAlphaId(searchForTag(ComprehensionTlvTag.ALPHA_ID, list));
        ComprehensionTlv searchForTag2 = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag2 != null) {
            iconId = ValueParser.retrieveIconId(searchForTag2);
            textMessage.iconSelfExplanatory = iconId.selfExplanatory;
        }
        switch (commandDetails.commandQualifier) {
            case 2:
                launchBrowserMode = LaunchBrowserMode.USE_EXISTING_BROWSER;
                break;
            case 3:
                launchBrowserMode = LaunchBrowserMode.LAUNCH_NEW_BROWSER;
                break;
            default:
                launchBrowserMode = LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED;
                break;
        }
        this.mCmdParams = new LaunchBrowserParams(commandDetails, textMessage, gsm8BitUnpackedToString, launchBrowserMode);
        if (iconId == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processPlayTone(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        Tone tone;
        IconId iconId;
        CatLog.d((Object) this, "process PlayTone");
        TextMessage textMessage = new TextMessage();
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.TONE, list);
        if (searchForTag == null || searchForTag.getLength() <= 0) {
            tone = null;
        } else {
            try {
                tone = Tone.fromInt(searchForTag.getRawValue()[searchForTag.getValueIndex()]);
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        searchForTag = searchForTag(ComprehensionTlvTag.ALPHA_ID, list);
        if (searchForTag != null) {
            textMessage.text = ValueParser.retrieveAlphaId(searchForTag);
            if (textMessage.text == null) {
                textMessage.text = "";
            }
        }
        searchForTag = searchForTag(ComprehensionTlvTag.DURATION, list);
        Duration retrieveDuration = searchForTag != null ? ValueParser.retrieveDuration(searchForTag) : null;
        searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag != null) {
            IconId retrieveIconId = ValueParser.retrieveIconId(searchForTag);
            textMessage.iconSelfExplanatory = retrieveIconId.selfExplanatory;
            iconId = retrieveIconId;
        } else {
            iconId = null;
        }
        boolean z = (commandDetails.commandQualifier & 1) != 0;
        textMessage.responseNeeded = false;
        this.mCmdParams = new PlayToneParams(commandDetails, textMessage, tone, retrieveDuration, z);
        if (iconId == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processProvideLocalInfo(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process ProvideLocalInfo");
        switch (commandDetails.commandQualifier) {
            case 3:
                CatLog.d((Object) this, "PLI [DTTZ_SETTING]");
                this.mCmdParams = new CommandParams(commandDetails);
                break;
            case 4:
                CatLog.d((Object) this, "PLI [LANGUAGE_SETTING]");
                this.mCmdParams = new CommandParams(commandDetails);
                break;
            default:
                CatLog.d((Object) this, "PLI[" + commandDetails.commandQualifier + "] Command Not Supported");
                this.mCmdParams = new CommandParams(commandDetails);
                throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }
        return false;
    }

    private boolean processSelectItem(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        ItemsIconId itemsIconId = null;
        CatLog.d((Object) this, "process SelectItem");
        Menu menu = new Menu();
        Iterator it = list.iterator();
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.ALPHA_ID, list);
        if (searchForTag != null) {
            menu.title = ValueParser.retrieveAlphaId(searchForTag);
        }
        while (true) {
            searchForTag = searchForNextTag(ComprehensionTlvTag.ITEM, it);
            if (searchForTag == null) {
                break;
            }
            menu.items.add(ValueParser.retrieveItem(searchForTag));
        }
        if (menu.items.size() == 0) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        IconId retrieveIconId;
        ComprehensionTlv searchForTag2 = searchForTag(ComprehensionTlvTag.ITEM_ID, list);
        if (searchForTag2 != null) {
            menu.defaultItem = ValueParser.retrieveItemId(searchForTag2) - 1;
        }
        searchForTag2 = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag2 != null) {
            this.mIconLoadState = 1;
            retrieveIconId = ValueParser.retrieveIconId(searchForTag2);
            menu.titleIconSelfExplanatory = retrieveIconId.selfExplanatory;
        } else {
            retrieveIconId = null;
        }
        searchForTag = searchForTag(ComprehensionTlvTag.ITEM_ICON_ID_LIST, list);
        if (searchForTag != null) {
            this.mIconLoadState = 2;
            itemsIconId = ValueParser.retrieveItemsIconId(searchForTag);
            menu.itemsIconSelfExplanatory = itemsIconId.selfExplanatory;
        }
        if (((commandDetails.commandQualifier & 1) != 0 ? 1 : 0) != 0) {
            if ((commandDetails.commandQualifier & 2) == 0) {
                menu.presentationType = PresentationType.DATA_VALUES;
            } else {
                menu.presentationType = PresentationType.NAVIGATION_OPTIONS;
            }
        }
        menu.softKeyPreferred = (commandDetails.commandQualifier & 4) != 0;
        menu.helpAvailable = (commandDetails.commandQualifier & 128) != 0;
        this.mCmdParams = new SelectItemParams(commandDetails, menu, retrieveIconId != null);
        switch (this.mIconLoadState) {
            case 0:
                return false;
            case 1:
                this.mloadIcon = true;
                this.mIconLoader.loadIcon(retrieveIconId.recordNumber, obtainMessage(1));
                return true;
            case 2:
                int[] iArr = itemsIconId.recordNumbers;
                if (retrieveIconId != null) {
                    iArr = new int[(itemsIconId.recordNumbers.length + 1)];
                    iArr[0] = retrieveIconId.recordNumber;
                    System.arraycopy(itemsIconId.recordNumbers, 0, iArr, 1, itemsIconId.recordNumbers.length);
                }
                int[] iArr2 = iArr;
                this.mloadIcon = true;
                this.mIconLoader.loadIcons(iArr2, obtainMessage(1));
                return true;
            default:
                return true;
        }
    }

    private boolean processSetUpEventList(CommandDetails commandDetails, List<ComprehensionTlv> list) {
        CatLog.d((Object) this, "process SetUpEventList");
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.EVENT_LIST, list);
        if (searchForTag != null) {
            try {
                byte[] rawValue = searchForTag.getRawValue();
                int valueIndex = searchForTag.getValueIndex();
                int length = searchForTag.getLength();
                int[] iArr = new int[length];
                int i = 0;
                while (length > 0) {
                    int i2 = rawValue[valueIndex] & 255;
                    valueIndex++;
                    length--;
                    switch (i2) {
                        case 4:
                        case 5:
                        case 7:
                        case 8:
                        case 15:
                        case 19:
                            iArr[i] = i2;
                            i++;
                            break;
                        default:
                            break;
                    }
                }
                this.mCmdParams = new SetEventListParams(commandDetails, iArr);
            } catch (IndexOutOfBoundsException e) {
                CatLog.e((Object) this, " IndexOutofBoundException in processSetUpEventList");
            }
        }
        return false;
    }

    private boolean processSetUpIdleModeText(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        CatLog.d((Object) this, "process SetUpIdleModeText");
        TextMessage textMessage = new TextMessage();
        IconId iconId = null;
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.TEXT_STRING, list);
        if (searchForTag != null) {
            textMessage.text = ValueParser.retrieveTextString(searchForTag);
        }
        searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag != null) {
            iconId = ValueParser.retrieveIconId(searchForTag);
            textMessage.iconSelfExplanatory = iconId.selfExplanatory;
        }
        if (textMessage.text != null || iconId == null || textMessage.iconSelfExplanatory) {
            this.mCmdParams = new DisplayTextParams(commandDetails, textMessage);
            if (iconId == null) {
                return false;
            }
            this.mloadIcon = true;
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
    }

    private boolean processSetupCall(CommandDetails commandDetails, List<ComprehensionTlv> list) throws ResultException {
        IconId retrieveIconId;
        IconId iconId = null;
        CatLog.d((Object) this, "process SetupCall");
        Iterator it = list.iterator();
        TextMessage textMessage = new TextMessage();
        TextMessage textMessage2 = new TextMessage();
        textMessage.text = ValueParser.retrieveAlphaId(searchForNextTag(ComprehensionTlvTag.ALPHA_ID, it));
        ComprehensionTlv searchForTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForTag != null) {
            retrieveIconId = ValueParser.retrieveIconId(searchForTag);
            textMessage.iconSelfExplanatory = retrieveIconId.selfExplanatory;
        } else {
            retrieveIconId = null;
        }
        ComprehensionTlv searchForNextTag = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, it);
        if (searchForNextTag != null) {
            textMessage2.text = ValueParser.retrieveAlphaId(searchForNextTag);
        }
        searchForNextTag = searchForTag(ComprehensionTlvTag.ICON_ID, list);
        if (searchForNextTag != null) {
            iconId = ValueParser.retrieveIconId(searchForNextTag);
            textMessage2.iconSelfExplanatory = iconId.selfExplanatory;
        }
        this.mCmdParams = new CallSetupParams(commandDetails, textMessage, textMessage2);
        if (retrieveIconId == null && iconId == null) {
            return false;
        }
        this.mIconLoadState = 2;
        int i = retrieveIconId != null ? retrieveIconId.recordNumber : -1;
        int i2 = iconId != null ? iconId.recordNumber : -1;
        this.mIconLoader.loadIcons(new int[]{i, i2}, obtainMessage(1));
        return true;
    }

    private ComprehensionTlv searchForNextTag(ComprehensionTlvTag comprehensionTlvTag, Iterator<ComprehensionTlv> it) {
        int value = comprehensionTlvTag.value();
        while (it.hasNext()) {
            ComprehensionTlv comprehensionTlv = (ComprehensionTlv) it.next();
            if (comprehensionTlv.getTag() == value) {
                return comprehensionTlv;
            }
        }
        return null;
    }

    private ComprehensionTlv searchForTag(ComprehensionTlvTag comprehensionTlvTag, List<ComprehensionTlv> list) {
        return searchForNextTag(comprehensionTlvTag, list.iterator());
    }

    private void sendCmdParams(ResultCode resultCode) {
        this.mCaller.sendMsgParamsDecoded(resultCode, this.mCmdParams);
    }

    private ResultCode setIcons(Object obj) {
        int i = 0;
        if (obj != null) {
            switch (this.mIconLoadState) {
                case 1:
                    this.mCmdParams.setIcon((Bitmap) obj);
                    break;
                case 2:
                    Bitmap[] bitmapArr = (Bitmap[]) obj;
                    int length = bitmapArr.length;
                    while (i < length) {
                        Bitmap bitmap = bitmapArr[i];
                        this.mCmdParams.setIcon(bitmap);
                        if (bitmap == null && this.mloadIcon) {
                            CatLog.d((Object) this, "Optional Icon data is NULL while loading multi icons");
                            this.mCmdParams.mLoadIconFailed = true;
                        }
                        i++;
                    }
                    break;
            }
            return ResultCode.OK;
        } else if (!this.mloadIcon) {
            return ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
        } else {
            CatLog.d((Object) this, "Optional Icon data is NULL");
            this.mCmdParams.mLoadIconFailed = true;
            this.mloadIcon = false;
            return ResultCode.OK;
        }
    }

    public void dispose() {
        this.mIconLoader.dispose();
        this.mIconLoader = null;
        this.mCmdParams = null;
        this.mCaller = null;
        sInstance = null;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1:
                sendCmdParams(setIcons(message.obj));
                return;
            default:
                return;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void make(BerTlv berTlv) {
        if (berTlv != null) {
            this.mCmdParams = null;
            this.mIconLoadState = 0;
            if (berTlv.getTag() != BerTlv.BER_PROACTIVE_COMMAND_TAG) {
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
            List comprehensionTlvs = berTlv.getComprehensionTlvs();
            CommandDetails processCommandDetails = processCommandDetails(comprehensionTlvs);
            if (processCommandDetails == null) {
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
            CommandType fromInt = CommandType.fromInt(processCommandDetails.typeOfCommand);
            if (fromInt == null) {
                this.mCmdParams = new CommandParams(processCommandDetails);
                sendCmdParams(ResultCode.BEYOND_TERMINAL_CAPABILITY);
            } else if (berTlv.isLengthValid()) {
                try {
                    boolean processSelectItem;
                    switch (fromInt) {
                        case SET_UP_MENU:
                            processSelectItem = processSelectItem(processCommandDetails, comprehensionTlvs);
                            break;
                        case SELECT_ITEM:
                            processSelectItem = processSelectItem(processCommandDetails, comprehensionTlvs);
                            break;
                        case DISPLAY_TEXT:
                            processSelectItem = processDisplayText(processCommandDetails, comprehensionTlvs);
                            break;
                        case SET_UP_IDLE_MODE_TEXT:
                            processSelectItem = processSetUpIdleModeText(processCommandDetails, comprehensionTlvs);
                            break;
                        case GET_INKEY:
                            processSelectItem = processGetInkey(processCommandDetails, comprehensionTlvs);
                            break;
                        case GET_INPUT:
                            processSelectItem = processGetInput(processCommandDetails, comprehensionTlvs);
                            break;
                        case SEND_DTMF:
                        case SEND_SMS:
                        case SEND_SS:
                        case SEND_USSD:
                            processSelectItem = processEventNotify(processCommandDetails, comprehensionTlvs);
                            break;
                        case GET_CHANNEL_STATUS:
                        case SET_UP_CALL:
                            processSelectItem = processSetupCall(processCommandDetails, comprehensionTlvs);
                            break;
                        case REFRESH:
                            processSelectItem = processEventNotify(processCommandDetails, comprehensionTlvs);
                            break;
                        case LAUNCH_BROWSER:
                            processSelectItem = processLaunchBrowser(processCommandDetails, comprehensionTlvs);
                            break;
                        case PLAY_TONE:
                            processSelectItem = processPlayTone(processCommandDetails, comprehensionTlvs);
                            break;
                        case SET_UP_EVENT_LIST:
                            processSelectItem = processSetUpEventList(processCommandDetails, comprehensionTlvs);
                            break;
                        case PROVIDE_LOCAL_INFORMATION:
                            processSelectItem = processProvideLocalInfo(processCommandDetails, comprehensionTlvs);
                            break;
                        case OPEN_CHANNEL:
                        case CLOSE_CHANNEL:
                        case RECEIVE_DATA:
                        case SEND_DATA:
                            processSelectItem = processBIPClient(processCommandDetails, comprehensionTlvs);
                            break;
                        case ACTIVATE:
                            processSelectItem = processActivate(processCommandDetails, comprehensionTlvs);
                            break;
                        default:
                            this.mCmdParams = new CommandParams(processCommandDetails);
                            sendCmdParams(ResultCode.BEYOND_TERMINAL_CAPABILITY);
                            return;
                    }
                    if (!processSelectItem) {
                        sendCmdParams(ResultCode.OK);
                    }
                } catch (ResultException e) {
                    CatLog.d((Object) this, "make: caught ResultException e=" + e);
                    this.mCmdParams = new CommandParams(processCommandDetails);
                    sendCmdParams(e.result());
                }
            } else {
                this.mCmdParams = new CommandParams(processCommandDetails);
                sendCmdParams(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
    }
}
