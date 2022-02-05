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
public class SpnOverride {
    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH = "etc/spn-conf.xml";
    private HashMap<String, String> mCarrierSpnMap = new HashMap<>();

    public SpnOverride() {
        loadSpnOverrides();
    }

    public boolean containsCarrier(String carrier) {
        return this.mCarrierSpnMap.containsKey(carrier);
    }

    public String getSpn(String carrier) {
        return this.mCarrierSpnMap.get(carrier);
    }

    private void loadSpnOverrides() {
        try {
            FileReader spnReader = new FileReader(new File(Environment.getRootDirectory(), PARTNER_SPN_OVERRIDE_PATH));
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(spnReader);
                XmlUtils.beginDocument(parser, "spnOverrides");
                while (true) {
                    XmlUtils.nextElement(parser);
                    if (!"spnOverride".equals(parser.getName())) {
                        spnReader.close();
                        return;
                    }
                    this.mCarrierSpnMap.put(parser.getAttributeValue(null, Telephony.Carriers.NUMERIC), parser.getAttributeValue(null, "spn"));
                }
            } catch (IOException e) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
            } catch (XmlPullParserException e2) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e2);
            }
        } catch (FileNotFoundException e3) {
            Rlog.w(LOG_TAG, "Can not open " + Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
        }
    }
}
