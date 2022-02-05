package com.android.internal.telephony.uicc;

import android.os.Environment;
import android.provider.Telephony;
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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class VoiceMailConstants {
    static final String LOG_TAG = "VoiceMailConstants";
    static final int NAME = 0;
    static final int NUMBER = 1;
    static final String PARTNER_VOICEMAIL_PATH = "etc/voicemail-conf.xml";
    static final int SIZE = 3;
    static final int TAG = 2;
    private HashMap<String, String[]> CarrierVmMap = new HashMap<>();

    /* JADX INFO: Access modifiers changed from: package-private */
    public VoiceMailConstants() {
        loadVoiceMail();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean containsCarrier(String carrier) {
        return this.CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        return this.CarrierVmMap.get(carrier)[0];
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getVoiceMailNumber(String carrier) {
        return this.CarrierVmMap.get(carrier)[1];
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getVoiceMailTag(String carrier) {
        return this.CarrierVmMap.get(carrier)[2];
    }

    private void loadVoiceMail() {
        FileReader vmReader;
        try {
            try {
                vmReader = new FileReader(new File(Environment.getRootDirectory(), PARTNER_VOICEMAIL_PATH));
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(vmReader);
                    XmlUtils.beginDocument(parser, "voicemail");
                    while (true) {
                        XmlUtils.nextElement(parser);
                        if (!"voicemail".equals(parser.getName())) {
                            break;
                        }
                        this.CarrierVmMap.put(parser.getAttributeValue(null, Telephony.Carriers.NUMERIC), new String[]{parser.getAttributeValue(null, "carrier"), parser.getAttributeValue(null, "vmnumber"), parser.getAttributeValue(null, "vmtag")});
                    }
                    if (vmReader != null) {
                        try {
                            vmReader.close();
                        } catch (IOException e) {
                        }
                    }
                } catch (IOException e2) {
                    Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e2);
                    if (vmReader != null) {
                        try {
                            vmReader.close();
                        } catch (IOException e3) {
                        }
                    }
                } catch (XmlPullParserException e4) {
                    Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e4);
                    if (vmReader != null) {
                        try {
                            vmReader.close();
                        } catch (IOException e5) {
                        }
                    }
                }
            } catch (FileNotFoundException e6) {
                Rlog.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
            }
        } catch (Throwable th) {
            if (vmReader != null) {
                try {
                    vmReader.close();
                } catch (IOException e7) {
                }
            }
            throw th;
        }
    }
}
