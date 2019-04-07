package com.android.internal.telephony.cat;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.uicc.IccUtils;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

abstract class ValueParser {
    ValueParser() {
    }

    static String retrieveAlphaId(ComprehensionTlv comprehensionTlv) throws ResultException {
        if (comprehensionTlv != null) {
            byte[] rawValue = comprehensionTlv.getRawValue();
            int valueIndex = comprehensionTlv.getValueIndex();
            int length = comprehensionTlv.getLength();
            if (length != 0) {
                try {
                    return IccUtils.adnStringFieldToString(rawValue, valueIndex, length);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            CatLog.d("ValueParser", "Alpha Id length=" + length);
            return null;
        }
        boolean z;
        try {
            z = Resources.getSystem().getBoolean(17956989);
        } catch (NotFoundException e2) {
            z = false;
        }
        return !z ? "Default Message" : null;
    }

    static CommandDetails retrieveCommandDetails(ComprehensionTlv comprehensionTlv) throws ResultException {
        CommandDetails commandDetails = new CommandDetails();
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        try {
            commandDetails.compRequired = comprehensionTlv.isComprehensionRequired();
            commandDetails.commandNumber = rawValue[valueIndex] & 255;
            commandDetails.typeOfCommand = rawValue[valueIndex + 1] & 255;
            commandDetails.commandQualifier = rawValue[valueIndex + 2] & 255;
            return commandDetails;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static DeviceIdentities retrieveDeviceIdentities(ComprehensionTlv comprehensionTlv) throws ResultException {
        DeviceIdentities deviceIdentities = new DeviceIdentities();
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        try {
            deviceIdentities.sourceId = rawValue[valueIndex] & 255;
            deviceIdentities.destinationId = rawValue[valueIndex + 1] & 255;
            return deviceIdentities;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
    }

    static Duration retrieveDuration(ComprehensionTlv comprehensionTlv) throws ResultException {
        TimeUnit timeUnit = TimeUnit.SECOND;
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        try {
            return new Duration(rawValue[valueIndex + 1] & 255, TimeUnit.values()[rawValue[valueIndex] & 255]);
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static IconId retrieveIconId(ComprehensionTlv comprehensionTlv) throws ResultException {
        IconId iconId = new IconId();
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        try {
            iconId.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            iconId.recordNumber = rawValue[valueIndex + 1] & 255;
            return iconId;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static Item retrieveItem(ComprehensionTlv comprehensionTlv) throws ResultException {
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        int length = comprehensionTlv.getLength();
        if (length == 0) {
            return null;
        }
        try {
            return new Item(rawValue[valueIndex] & 255, IccUtils.adnStringFieldToString(rawValue, valueIndex + 1, length - 1));
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static int retrieveItemId(ComprehensionTlv comprehensionTlv) throws ResultException {
        return comprehensionTlv.getRawValue()[comprehensionTlv.getValueIndex()] & 255;
    }

    static ItemsIconId retrieveItemsIconId(ComprehensionTlv comprehensionTlv) throws ResultException {
        int i = 0;
        CatLog.d("ValueParser", "retrieveItemsIconId:");
        ItemsIconId itemsIconId = new ItemsIconId();
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        int length = comprehensionTlv.getLength() - 1;
        itemsIconId.recordNumbers = new int[length];
        int i2 = valueIndex + 1;
        try {
            itemsIconId.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            valueIndex = i2;
            while (i < length) {
                itemsIconId.recordNumbers[i] = rawValue[valueIndex];
                i++;
                valueIndex++;
            }
            return itemsIconId;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static int retrieveTarget(ComprehensionTlv comprehensionTlv) throws ResultException {
        ActivateDescriptor activateDescriptor = new ActivateDescriptor();
        try {
            activateDescriptor.target = comprehensionTlv.getRawValue()[comprehensionTlv.getValueIndex()] & 255;
            return activateDescriptor.target;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
    }

    static List<TextAttribute> retrieveTextAttribute(ComprehensionTlv comprehensionTlv) throws ResultException {
        ArrayList arrayList = new ArrayList();
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        int length = comprehensionTlv.getLength();
        if (length == 0) {
            return null;
        }
        int i = length / 4;
        int i2 = 0;
        int i3 = valueIndex;
        while (i2 < i) {
            byte b = rawValue[i3];
            byte b2 = rawValue[i3 + 1];
            length = rawValue[i3 + 2] & 255;
            byte b3 = rawValue[i3 + 3];
            try {
                TextAlignment fromInt = TextAlignment.fromInt(length & 3);
                FontSize fromInt2 = FontSize.fromInt((length >> 2) & 3);
                if (fromInt2 == null) {
                    fromInt2 = FontSize.NORMAL;
                }
                arrayList.add(new TextAttribute(b & 255, b2 & 255, fromInt, fromInt2, (length & 16) != 0, (length & 32) != 0, (length & 64) != 0, (length & 128) != 0, TextColor.fromInt(b3 & 255)));
                i2++;
                i3 += 4;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        return arrayList;
    }

    static String retrieveTextString(ComprehensionTlv comprehensionTlv) throws ResultException {
        byte[] rawValue = comprehensionTlv.getRawValue();
        int valueIndex = comprehensionTlv.getValueIndex();
        int length = comprehensionTlv.getLength();
        if (length == 0) {
            return null;
        }
        int i = length - 1;
        byte b = (byte) (rawValue[valueIndex] & 12);
        if (b == (byte) 0) {
            try {
                return GsmAlphabet.gsm7BitPackedToString(rawValue, valueIndex + 1, (i * 8) / 7);
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            } catch (UnsupportedEncodingException e2) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        } else if (b == (byte) 4) {
            return GsmAlphabet.gsm8BitUnpackedToString(rawValue, valueIndex + 1, i);
        } else {
            if (b == (byte) 8) {
                return new String(rawValue, valueIndex + 1, i, "UTF-16");
            }
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }
}
