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

public class SpnOverride {
    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH = "etc/spn-conf.xml";
    private HashMap<String, String> mCarrierSpnMap = new HashMap();

    public SpnOverride() {
        loadSpnOverrides();
    }

    private void loadSpnOverrides() {
        try {
            FileReader fileReader = new FileReader(new File(Environment.getRootDirectory(), PARTNER_SPN_OVERRIDE_PATH));
            try {
                XmlPullParser newPullParser = Xml.newPullParser();
                newPullParser.setInput(fileReader);
                XmlUtils.beginDocument(newPullParser, "spnOverrides");
                while (true) {
                    XmlUtils.nextElement(newPullParser);
                    if ("spnOverride".equals(newPullParser.getName())) {
                        this.mCarrierSpnMap.put(newPullParser.getAttributeValue(null, Carriers.NUMERIC), newPullParser.getAttributeValue(null, "spn"));
                    } else {
                        fileReader.close();
                        return;
                    }
                }
            } catch (XmlPullParserException e) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
            } catch (IOException e2) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e2);
            }
        } catch (FileNotFoundException e3) {
            Rlog.w(LOG_TAG, "Can not open " + Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
        }
    }

    public boolean containsCarrier(String str) {
        return this.mCarrierSpnMap.containsKey(str);
    }

    public String getSpn(String str) {
        return (String) this.mCarrierSpnMap.get(str);
    }
}
