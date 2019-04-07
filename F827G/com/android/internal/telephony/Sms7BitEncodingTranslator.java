package com.android.internal.telephony;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.SparseIntArray;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.XmlUtils;

public class Sms7BitEncodingTranslator {
    private static final boolean DBG = Build.IS_DEBUGGABLE;
    private static final String TAG = "Sms7BitEncodingTranslator";
    private static final String XML_CHARACTOR_TAG = "Character";
    private static final String XML_FROM_TAG = "from";
    private static final String XML_START_TAG = "SmsEnforce7BitTranslationTable";
    private static final String XML_TO_TAG = "to";
    private static final String XML_TRANSLATION_TYPE_TAG = "TranslationType";
    private static boolean mIs7BitTranslationTableLoaded = false;
    private static SparseIntArray mTranslationTable = null;
    private static SparseIntArray mTranslationTableCDMA = null;
    private static SparseIntArray mTranslationTableCommon = null;
    private static SparseIntArray mTranslationTableGSM = null;

    private static void load7BitTranslationTableFromXml() {
        Resources system = Resources.getSystem();
        if (DBG) {
            Rlog.d(TAG, "load7BitTranslationTableFromXml: open normal file");
        }
        XmlResourceParser xml = system.getXml(17891344);
        try {
            XmlUtils.beginDocument(xml, XML_START_TAG);
            while (true) {
                XmlUtils.nextElement(xml);
                String name = xml.getName();
                if (DBG) {
                    Rlog.d(TAG, "tag: " + name);
                }
                if (XML_TRANSLATION_TYPE_TAG.equals(name)) {
                    name = xml.getAttributeValue(null, "Type");
                    if (DBG) {
                        Rlog.d(TAG, "type: " + name);
                    }
                    if (name.equals("common")) {
                        mTranslationTable = mTranslationTableCommon;
                    } else if (name.equals("gsm")) {
                        mTranslationTable = mTranslationTableGSM;
                    } else if (name.equals("cdma")) {
                        mTranslationTable = mTranslationTableCDMA;
                    } else {
                        Rlog.e(TAG, "Error Parsing 7BitTranslationTable: found incorrect type" + name);
                    }
                } else if (XML_CHARACTOR_TAG.equals(name) && mTranslationTable != null) {
                    int attributeUnsignedIntValue = xml.getAttributeUnsignedIntValue(null, XML_FROM_TAG, -1);
                    int attributeUnsignedIntValue2 = xml.getAttributeUnsignedIntValue(null, XML_TO_TAG, -1);
                    if (attributeUnsignedIntValue == -1 || attributeUnsignedIntValue2 == -1) {
                        Rlog.d(TAG, "Invalid translation table file format");
                    } else {
                        if (DBG) {
                            Rlog.d(TAG, "Loading mapping " + Integer.toHexString(attributeUnsignedIntValue).toUpperCase() + " -> " + Integer.toHexString(attributeUnsignedIntValue2).toUpperCase());
                        }
                        mTranslationTable.put(attributeUnsignedIntValue, attributeUnsignedIntValue2);
                    }
                }
            }
            if (DBG) {
                Rlog.d(TAG, "load7BitTranslationTableFromXml: parsing successful, file loaded");
            }
            if (xml instanceof XmlResourceParser) {
                xml.close();
            }
        } catch (Exception e) {
            Rlog.e(TAG, "Got exception while loading 7BitTranslationTable file.", e);
            if (xml instanceof XmlResourceParser) {
                xml.close();
            }
        } catch (Throwable th) {
            if (xml instanceof XmlResourceParser) {
                xml.close();
            }
            throw th;
        }
    }

    private static boolean noTranslationNeeded(char c, boolean z) {
        return z ? GsmAlphabet.isGsmSeptets(c) && UserData.charToAscii.get(c, -1) != -1 : GsmAlphabet.isGsmSeptets(c);
    }

    public static String translate(CharSequence charSequence) {
        if (charSequence == null) {
            Rlog.w(TAG, "Null message can not be translated");
        } else {
            int length = charSequence.length();
            if (length <= 0) {
                return "";
            }
            if (!mIs7BitTranslationTableLoaded) {
                mTranslationTableCommon = new SparseIntArray();
                mTranslationTableGSM = new SparseIntArray();
                mTranslationTableCDMA = new SparseIntArray();
                load7BitTranslationTableFromXml();
                mIs7BitTranslationTableLoaded = true;
            }
            if ((mTranslationTableCommon != null && mTranslationTableCommon.size() > 0) || ((mTranslationTableGSM != null && mTranslationTableGSM.size() > 0) || (mTranslationTableCDMA != null && mTranslationTableCDMA.size() > 0))) {
                char[] cArr = new char[length];
                boolean useCdmaFormatForMoSms = useCdmaFormatForMoSms();
                for (int i = 0; i < length; i++) {
                    cArr[i] = translateIfNeeded(charSequence.charAt(i), useCdmaFormatForMoSms);
                }
                return String.valueOf(cArr);
            }
        }
        return null;
    }

    private static char translateIfNeeded(char c, boolean z) {
        if (!noTranslationNeeded(c, z)) {
            int i = mTranslationTableCommon != null ? mTranslationTableCommon.get(c, -1) : -1;
            if (i == -1) {
                if (z) {
                    if (mTranslationTableCDMA != null) {
                        i = mTranslationTableCDMA.get(c, -1);
                    }
                } else if (mTranslationTableGSM != null) {
                    i = mTranslationTableGSM.get(c, -1);
                }
            }
            if (i != -1) {
                if (DBG) {
                    Rlog.v(TAG, Integer.toHexString(c) + " (" + c + ")" + " translated to " + Integer.toHexString(i) + " (" + ((char) i) + ")");
                }
                return (char) i;
            }
            if (DBG) {
                Rlog.w(TAG, "No translation found for " + Integer.toHexString(c) + "! Replacing for empty space");
            }
            return ' ';
        } else if (!DBG) {
            return c;
        } else {
            Rlog.v(TAG, "No translation needed for " + Integer.toHexString(c));
            return c;
        }
    }

    private static boolean useCdmaFormatForMoSms() {
        return !SmsManager.getDefault().isImsSmsSupported() ? TelephonyManager.getDefault().getCurrentPhoneType() == 2 : SmsMessage.FORMAT_3GPP2.equals(SmsManager.getDefault().getImsSmsFormat());
    }
}
