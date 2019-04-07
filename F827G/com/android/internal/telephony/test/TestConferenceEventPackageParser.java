package com.android.internal.telephony.test;

import android.os.Bundle;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.TextBasedSmsColumns;
import android.util.Log;
import android.util.Xml;
import com.android.ims.ImsConferenceState;
import com.android.internal.util.XmlUtils;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class TestConferenceEventPackageParser {
    private static final String LOG_TAG = "TestConferenceEventPackageParser";
    private static final String PARTICIPANT_TAG = "participant";
    private InputStream mInputStream;

    public TestConferenceEventPackageParser(InputStream inputStream) {
        this.mInputStream = inputStream;
    }

    private Bundle parseParticipant(XmlPullParser xmlPullParser) throws IOException, XmlPullParserException {
        Bundle bundle = new Bundle();
        String str = "";
        String str2 = "";
        String str3 = "";
        String str4 = "";
        int depth = xmlPullParser.getDepth();
        while (XmlUtils.nextElementWithin(xmlPullParser, depth)) {
            if (xmlPullParser.getName().equals(Carriers.USER)) {
                xmlPullParser.next();
                str = xmlPullParser.getText();
            } else if (xmlPullParser.getName().equals("display-text")) {
                xmlPullParser.next();
                str2 = xmlPullParser.getText();
            } else if (xmlPullParser.getName().equals("endpoint")) {
                xmlPullParser.next();
                str3 = xmlPullParser.getText();
            } else if (xmlPullParser.getName().equals(TextBasedSmsColumns.STATUS)) {
                xmlPullParser.next();
                str4 = xmlPullParser.getText();
            }
        }
        Log.v(LOG_TAG, "User: " + str);
        Log.v(LOG_TAG, "DisplayText: " + str2);
        Log.v(LOG_TAG, "Endpoint: " + str3);
        Log.v(LOG_TAG, "Status: " + str4);
        bundle.putString(Carriers.USER, str);
        bundle.putString("display-text", str2);
        bundle.putString("endpoint", str3);
        bundle.putString(TextBasedSmsColumns.STATUS, str4);
        return bundle;
    }

    public ImsConferenceState parse() {
        ImsConferenceState imsConferenceState = new ImsConferenceState();
        try {
            XmlPullParser newPullParser = Xml.newPullParser();
            newPullParser.setInput(this.mInputStream, null);
            newPullParser.nextTag();
            int depth = newPullParser.getDepth();
            while (XmlUtils.nextElementWithin(newPullParser, depth)) {
                if (newPullParser.getName().equals(PARTICIPANT_TAG)) {
                    Log.v(LOG_TAG, "Found participant.");
                    Bundle parseParticipant = parseParticipant(newPullParser);
                    imsConferenceState.mParticipants.put(parseParticipant.getString("endpoint"), parseParticipant);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(LOG_TAG, "Failed to read test conference event package from XML file", e);
            return null;
        } finally {
            try {
                this.mInputStream.close();
                return null;
            } catch (IOException e2) {
                Log.e(LOG_TAG, "Failed to close test conference event package InputStream", e2);
                return null;
            }
        }
        return imsConferenceState;
    }
}
