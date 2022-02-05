package com.android.internal.telephony.test;

import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import com.android.internal.util.XmlUtils;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class TestConferenceEventPackageParser {
    private static final String LOG_TAG = "TestConferenceEventPackageParser";
    private static final String PARTICIPANT_TAG = "participant";
    private InputStream mInputStream;

    public TestConferenceEventPackageParser(InputStream inputStream) {
        this.mInputStream = inputStream;
    }

    /*  JADX ERROR: JadxRuntimeException in pass: SSATransform
        jadx.core.utils.exceptions.JadxRuntimeException: Not initialized variable reg: 5, insn: 0x0079: MOVE  (r0 I:??[OBJECT, ARRAY] A[D('conferenceState' com.android.ims.ImsConferenceState)]) = (r5 I:??[OBJECT, ARRAY]), block:B:24:0x0072
        	at jadx.core.dex.visitors.ssa.SSATransform.renameVarsInBlock(SSATransform.java:171)
        	at jadx.core.dex.visitors.ssa.SSATransform.renameVariables(SSATransform.java:143)
        	at jadx.core.dex.visitors.ssa.SSATransform.process(SSATransform.java:60)
        	at jadx.core.dex.visitors.ssa.SSATransform.visit(SSATransform.java:41)
        */
    public com.android.ims.ImsConferenceState parse() {
        /*
            r8 = this;
            r5 = 0
            com.android.ims.ImsConferenceState r0 = new com.android.ims.ImsConferenceState
            r0.<init>()
            org.xmlpull.v1.XmlPullParser r3 = android.util.Xml.newPullParser()     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            java.io.InputStream r6 = r8.mInputStream     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            r7 = 0
            r3.setInput(r6, r7)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            r3.nextTag()     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            int r2 = r3.getDepth()     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
        L_0x0017:
            boolean r6 = com.android.internal.util.XmlUtils.nextElementWithin(r3, r2)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            if (r6 == 0) goto L_0x0050
            java.lang.String r6 = r3.getName()     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            java.lang.String r7 = "participant"
            boolean r6 = r6.equals(r7)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            if (r6 == 0) goto L_0x0017
            java.lang.String r6 = "TestConferenceEventPackageParser"
            java.lang.String r7 = "Found participant."
            android.util.Log.v(r6, r7)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            android.os.Bundle r4 = r8.parseParticipant(r3)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            java.util.HashMap r6 = r0.mParticipants     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            java.lang.String r7 = "endpoint"
            java.lang.String r7 = r4.getString(r7)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            r6.put(r7, r4)     // Catch: IOException -> 0x0040, XmlPullParserException -> 0x007b, all -> 0x006a
            goto L_0x0017
        L_0x0040:
            r6 = move-exception
            r1 = r6
        L_0x0042:
            java.lang.String r6 = "TestConferenceEventPackageParser"
            java.lang.String r7 = "Failed to read test conference event package from XML file"
            android.util.Log.e(r6, r7, r1)     // Catch: all -> 0x006a
            java.io.InputStream r6 = r8.mInputStream     // Catch: IOException -> 0x0060
            r6.close()     // Catch: IOException -> 0x0060
            r0 = r5
        L_0x004f:
            return r0
        L_0x0050:
            java.io.InputStream r6 = r8.mInputStream     // Catch: IOException -> 0x0056
            r6.close()     // Catch: IOException -> 0x0056
            goto L_0x004f
        L_0x0056:
            r1 = move-exception
            java.lang.String r6 = "TestConferenceEventPackageParser"
            java.lang.String r7 = "Failed to close test conference event package InputStream"
            android.util.Log.e(r6, r7, r1)
            r0 = r5
            goto L_0x004f
        L_0x0060:
            r1 = move-exception
            java.lang.String r6 = "TestConferenceEventPackageParser"
            java.lang.String r7 = "Failed to close test conference event package InputStream"
            android.util.Log.e(r6, r7, r1)
            r0 = r5
            goto L_0x004f
        L_0x006a:
            r6 = move-exception
            java.io.InputStream r7 = r8.mInputStream     // Catch: IOException -> 0x0071
            r7.close()     // Catch: IOException -> 0x0071
            throw r6
        L_0x0071:
            r1 = move-exception
            java.lang.String r6 = "TestConferenceEventPackageParser"
            java.lang.String r7 = "Failed to close test conference event package InputStream"
            android.util.Log.e(r6, r7, r1)
            r0 = r5
            goto L_0x004f
        L_0x007b:
            r6 = move-exception
            r1 = r6
            goto L_0x0042
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.test.TestConferenceEventPackageParser.parse():com.android.ims.ImsConferenceState");
    }

    private Bundle parseParticipant(XmlPullParser parser) throws IOException, XmlPullParserException {
        Bundle bundle = new Bundle();
        String user = "";
        String displayText = "";
        String endpoint = "";
        String status = "";
        int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            if (parser.getName().equals(Telephony.Carriers.USER)) {
                parser.next();
                user = parser.getText();
            } else if (parser.getName().equals("display-text")) {
                parser.next();
                displayText = parser.getText();
            } else if (parser.getName().equals("endpoint")) {
                parser.next();
                endpoint = parser.getText();
            } else if (parser.getName().equals(Telephony.TextBasedSmsColumns.STATUS)) {
                parser.next();
                status = parser.getText();
            }
        }
        Log.v(LOG_TAG, "User: " + user);
        Log.v(LOG_TAG, "DisplayText: " + displayText);
        Log.v(LOG_TAG, "Endpoint: " + endpoint);
        Log.v(LOG_TAG, "Status: " + status);
        bundle.putString(Telephony.Carriers.USER, user);
        bundle.putString("display-text", displayText);
        bundle.putString("endpoint", endpoint);
        bundle.putString(Telephony.TextBasedSmsColumns.STATUS, status);
        return bundle;
    }
}
