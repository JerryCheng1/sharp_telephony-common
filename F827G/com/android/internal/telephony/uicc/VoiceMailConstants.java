package com.android.internal.telephony.uicc;

import android.os.Environment;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

class VoiceMailConstants {
    static final String LOG_TAG = "VoiceMailConstants";
    static final int NAME = 0;
    static final int NUMBER = 1;
    static final String PARTNER_VOICEMAIL_PATH = "etc/voicemail-conf.xml";
    static final int SIZE = 3;
    static final int TAG = 2;
    private HashMap<String, String[]> CarrierVmMap = new HashMap();

    VoiceMailConstants() {
        loadVoiceMail();
    }

    private void loadVoiceMail() {
        try {
            FileReader fileReader = new FileReader(new File(Environment.getRootDirectory(), PARTNER_VOICEMAIL_PATH));
            try {
                XmlPullParser newPullParser = Xml.newPullParser();
                newPullParser.setInput(fileReader);
                XmlUtils.beginDocument(newPullParser, "voicemail");
                while (true) {
                    XmlUtils.nextElement(newPullParser);
                    if (!"voicemail".equals(newPullParser.getName())) {
                        break;
                    }
                    String attributeValue = newPullParser.getAttributeValue(null, Carriers.NUMERIC);
                    String attributeValue2 = newPullParser.getAttributeValue(null, "carrier");
                    String attributeValue3 = newPullParser.getAttributeValue(null, "vmnumber");
                    String attributeValue4 = newPullParser.getAttributeValue(null, "vmtag");
                    this.CarrierVmMap.put(attributeValue, new String[]{attributeValue2, attributeValue3, attributeValue4});
                }
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e) {
                    }
                }
            } catch (XmlPullParserException e2) {
                Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e2);
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (IOException e4) {
                Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e4);
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e5) {
                    }
                }
            } catch (Throwable th) {
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e6) {
                    }
                }
            }
        } catch (FileNotFoundException e7) {
            Rlog.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean containsCarrier(String str) {
        return this.CarrierVmMap.containsKey(str);
    }

    /* Access modifiers changed, original: 0000 */
    public String getCarrierName(String str) {
        return ((String[]) this.CarrierVmMap.get(str))[0];
    }

    /* Access modifiers changed, original: 0000 */
    public String getVoiceMailNumber(String str) {
        return ((String[]) this.CarrierVmMap.get(str))[1];
    }

    /* Access modifiers changed, original: 0000 */
    public String getVoiceMailTag(String str) {
        return ((String[]) this.CarrierVmMap.get(str))[2];
    }
}
