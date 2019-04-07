package com.android.internal.telephony.cdma;

import android.content.Context;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneBase;
import java.util.HashMap;

public final class EriManager {
    private static final boolean DBG = true;
    static final int ERI_FROM_FILE_SYSTEM = 1;
    static final int ERI_FROM_MODEM = 2;
    static final int ERI_FROM_XML = 0;
    private static final String LOG_TAG = "CDMA";
    private static final boolean VDBG = false;
    private Context mContext;
    private EriFile mEriFile;
    private int mEriFileSource = 0;
    private boolean mIsEriFileLoaded;

    class EriDisplayInformation {
        int mEriIconIndex;
        int mEriIconMode;
        String mEriIconText;

        EriDisplayInformation(int i, int i2, String str) {
            this.mEriIconIndex = i;
            this.mEriIconMode = i2;
            this.mEriIconText = str;
        }

        public String toString() {
            return "EriDisplayInformation: { IconIndex: " + this.mEriIconIndex + " EriIconMode: " + this.mEriIconMode + " EriIconText: " + this.mEriIconText + " }";
        }
    }

    class EriFile {
        String[] mCallPromptId = new String[]{"", "", ""};
        int mEriFileType = -1;
        int mNumberOfEriEntries = 0;
        HashMap<Integer, EriInfo> mRoamIndTable = new HashMap();
        int mVersionNumber = -1;

        EriFile() {
        }
    }

    public EriManager(PhoneBase phoneBase, Context context, int i) {
        this.mContext = context;
        this.mEriFileSource = i;
        this.mEriFile = new EriFile();
    }

    private EriDisplayInformation getEriDisplayInformation(int i, int i2) {
        EriInfo eriInfo;
        if (this.mIsEriFileLoaded) {
            eriInfo = getEriInfo(i);
            if (eriInfo != null) {
                return new EriDisplayInformation(eriInfo.iconIndex, eriInfo.iconMode, eriInfo.eriText);
            }
        }
        switch (i) {
            case 0:
                return new EriDisplayInformation(0, 0, this.mContext.getText(17039607).toString());
            case 1:
                return new EriDisplayInformation(1, 0, this.mContext.getText(17039608).toString());
            case 2:
                return new EriDisplayInformation(2, 1, this.mContext.getText(17039609).toString());
            case 3:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039610).toString());
            case 4:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039611).toString());
            case 5:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039612).toString());
            case 6:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039613).toString());
            case 7:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039614).toString());
            case 8:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039615).toString());
            case 9:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039616).toString());
            case 10:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039617).toString());
            case 11:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039618).toString());
            case 12:
                return new EriDisplayInformation(i, 0, this.mContext.getText(17039619).toString());
            default:
                if (this.mIsEriFileLoaded) {
                    eriInfo = getEriInfo(i);
                    EriInfo eriInfo2 = getEriInfo(i2);
                    if (eriInfo != null) {
                        return new EriDisplayInformation(eriInfo.iconIndex, eriInfo.iconMode, eriInfo.eriText);
                    }
                    if (eriInfo2 != null) {
                        return new EriDisplayInformation(eriInfo2.iconIndex, eriInfo2.iconMode, eriInfo2.eriText);
                    }
                    Rlog.e(LOG_TAG, "ERI defRoamInd " + i2 + " not found in ERI file ...on");
                    return new EriDisplayInformation(0, 0, this.mContext.getText(17039607).toString());
                }
                Rlog.d(LOG_TAG, "ERI File not loaded");
                if (i2 > 2) {
                    return new EriDisplayInformation(2, 1, this.mContext.getText(17039609).toString());
                }
                switch (i2) {
                    case 0:
                        return new EriDisplayInformation(0, 0, this.mContext.getText(17039607).toString());
                    case 1:
                        return new EriDisplayInformation(1, 0, this.mContext.getText(17039608).toString());
                    case 2:
                        return new EriDisplayInformation(2, 1, this.mContext.getText(17039609).toString());
                    default:
                        return new EriDisplayInformation(-1, -1, "ERI text");
                }
        }
    }

    private EriInfo getEriInfo(int i) {
        return this.mEriFile.mRoamIndTable.containsKey(Integer.valueOf(i)) ? (EriInfo) this.mEriFile.mRoamIndTable.get(Integer.valueOf(i)) : null;
    }

    private void loadEriFileFromFileSystem() {
    }

    private void loadEriFileFromModem() {
    }

    /* JADX WARNING: Removed duplicated region for block: B:7:0x002c  */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x00dc A:{SYNTHETIC, Splitter:B:28:0x00dc} */
    /* JADX WARNING: Removed duplicated region for block: B:68:0x0076 A:{SYNTHETIC, EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  } */
    /* JADX WARNING: Removed duplicated region for block: B:14:0x007c A:{Catch:{ Exception -> 0x0103, all -> 0x013c }} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x00b6  */
    /* JADX WARNING: Removed duplicated region for block: B:74:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x00be A:{SYNTHETIC, Splitter:B:20:0x00be} */
    /* JADX WARNING: Removed duplicated region for block: B:7:0x002c  */
    /* JADX WARNING: Removed duplicated region for block: B:68:0x0076 A:{SYNTHETIC, EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  } */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x00dc A:{SYNTHETIC, Splitter:B:28:0x00dc} */
    /* JADX WARNING: Removed duplicated region for block: B:14:0x007c A:{Catch:{ Exception -> 0x0103, all -> 0x013c }} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x00b6  */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x00be A:{SYNTHETIC, Splitter:B:20:0x00be} */
    /* JADX WARNING: Removed duplicated region for block: B:74:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:7:0x002c  */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x00dc A:{SYNTHETIC, Splitter:B:28:0x00dc} */
    /* JADX WARNING: Removed duplicated region for block: B:68:0x0076 A:{SYNTHETIC, EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  } */
    /* JADX WARNING: Removed duplicated region for block: B:14:0x007c A:{Catch:{ Exception -> 0x0103, all -> 0x013c }} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x00b6  */
    /* JADX WARNING: Removed duplicated region for block: B:74:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x00be A:{SYNTHETIC, Splitter:B:20:0x00be} */
    /* JADX WARNING: Removed duplicated region for block: B:7:0x002c  */
    /* JADX WARNING: Removed duplicated region for block: B:68:0x0076 A:{SYNTHETIC, EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  , EDGE_INSN: B:68:0x0076->B:12:0x0076 ?: BREAK  } */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x00dc A:{SYNTHETIC, Splitter:B:28:0x00dc} */
    /* JADX WARNING: Removed duplicated region for block: B:14:0x007c A:{Catch:{ Exception -> 0x0103, all -> 0x013c }} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x00b6  */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x00be A:{SYNTHETIC, Splitter:B:20:0x00be} */
    /* JADX WARNING: Removed duplicated region for block: B:74:? A:{SYNTHETIC, RETURN} */
    private void loadEriFileFromXml() {
        /*
        r12 = this;
        r1 = 0;
        r0 = r12.mContext;
        r2 = r0.getResources();
        r0 = "CDMA";
        r3 = "loadEriFileFromXml: check for alternate file";
        android.telephony.Rlog.d(r0, r3);	 Catch:{ FileNotFoundException -> 0x00c2, XmlPullParserException -> 0x00cf }
        r0 = new java.io.FileInputStream;	 Catch:{ FileNotFoundException -> 0x00c2, XmlPullParserException -> 0x00cf }
        r3 = 17040831; // 0x10405bf float:2.4248694E-38 double:8.419289E-317;
        r3 = r2.getString(r3);	 Catch:{ FileNotFoundException -> 0x00c2, XmlPullParserException -> 0x00cf }
        r0.<init>(r3);	 Catch:{ FileNotFoundException -> 0x00c2, XmlPullParserException -> 0x00cf }
        r7 = android.util.Xml.newPullParser();	 Catch:{ FileNotFoundException -> 0x01ac, XmlPullParserException -> 0x01af }
        r3 = 0;
        r7.setInput(r0, r3);	 Catch:{ FileNotFoundException -> 0x01ac, XmlPullParserException -> 0x01af }
        r3 = "CDMA";
        r4 = "loadEriFileFromXml: opened alternate file";
        android.telephony.Rlog.d(r3, r4);	 Catch:{ FileNotFoundException -> 0x01ac, XmlPullParserException -> 0x01af }
        r9 = r0;
    L_0x002a:
        if (r7 != 0) goto L_0x003a;
    L_0x002c:
        r0 = "CDMA";
        r1 = "loadEriFileFromXml: open normal file";
        android.telephony.Rlog.d(r0, r1);
        r0 = 17891332; // 0x1110004 float:2.6632305E-38 double:8.8394925E-317;
        r7 = r2.getXml(r0);
    L_0x003a:
        r0 = "EriFile";
        com.android.internal.util.XmlUtils.beginDocument(r7, r0);	 Catch:{ Exception -> 0x0103 }
        r0 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r1 = 0;
        r2 = "VersionNumber";
        r1 = r7.getAttributeValue(r1, r2);	 Catch:{ Exception -> 0x0103 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x0103 }
        r0.mVersionNumber = r1;	 Catch:{ Exception -> 0x0103 }
        r0 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r1 = 0;
        r2 = "NumberOfEriEntries";
        r1 = r7.getAttributeValue(r1, r2);	 Catch:{ Exception -> 0x0103 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x0103 }
        r0.mNumberOfEriEntries = r1;	 Catch:{ Exception -> 0x0103 }
        r0 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r1 = 0;
        r2 = "EriFileType";
        r1 = r7.getAttributeValue(r1, r2);	 Catch:{ Exception -> 0x0103 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x0103 }
        r0.mEriFileType = r1;	 Catch:{ Exception -> 0x0103 }
        r0 = 0;
    L_0x006d:
        com.android.internal.util.XmlUtils.nextElement(r7);	 Catch:{ Exception -> 0x0103 }
        r1 = r7.getName();	 Catch:{ Exception -> 0x0103 }
        if (r1 != 0) goto L_0x00dc;
    L_0x0076:
        r1 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r1 = r1.mNumberOfEriEntries;	 Catch:{ Exception -> 0x0103 }
        if (r0 == r1) goto L_0x00a8;
    L_0x007c:
        r1 = "CDMA";
        r2 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0103 }
        r2.<init>();	 Catch:{ Exception -> 0x0103 }
        r3 = "Error Parsing ERI file: ";
        r2 = r2.append(r3);	 Catch:{ Exception -> 0x0103 }
        r3 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r3 = r3.mNumberOfEriEntries;	 Catch:{ Exception -> 0x0103 }
        r2 = r2.append(r3);	 Catch:{ Exception -> 0x0103 }
        r3 = " defined, ";
        r2 = r2.append(r3);	 Catch:{ Exception -> 0x0103 }
        r0 = r2.append(r0);	 Catch:{ Exception -> 0x0103 }
        r2 = " parsed!";
        r0 = r0.append(r2);	 Catch:{ Exception -> 0x0103 }
        r0 = r0.toString();	 Catch:{ Exception -> 0x0103 }
        android.telephony.Rlog.e(r1, r0);	 Catch:{ Exception -> 0x0103 }
    L_0x00a8:
        r0 = "CDMA";
        r1 = "loadEriFileFromXml: eri parsing successful, file loaded";
        android.telephony.Rlog.d(r0, r1);	 Catch:{ Exception -> 0x0103 }
        r0 = 1;
        r12.mIsEriFileLoaded = r0;	 Catch:{ Exception -> 0x0103 }
        r0 = r7 instanceof android.content.res.XmlResourceParser;
        if (r0 == 0) goto L_0x00bc;
    L_0x00b6:
        r0 = r7;
        r0 = (android.content.res.XmlResourceParser) r0;
        r0.close();
    L_0x00bc:
        if (r9 == 0) goto L_0x00c1;
    L_0x00be:
        r9.close();	 Catch:{ IOException -> 0x01a7 }
    L_0x00c1:
        return;
    L_0x00c2:
        r0 = move-exception;
        r0 = r1;
    L_0x00c4:
        r3 = "CDMA";
        r4 = "loadEriFileFromXml: no alternate file";
        android.telephony.Rlog.d(r3, r4);
        r9 = r0;
        r7 = r1;
        goto L_0x002a;
    L_0x00cf:
        r0 = move-exception;
        r0 = r1;
    L_0x00d1:
        r3 = "CDMA";
        r4 = "loadEriFileFromXml: no parser for alternate file";
        android.telephony.Rlog.d(r3, r4);
        r9 = r0;
        r7 = r1;
        goto L_0x002a;
    L_0x00dc:
        r2 = "CallPromptId";
        r2 = r1.equals(r2);	 Catch:{ Exception -> 0x0103 }
        if (r2 == 0) goto L_0x014c;
    L_0x00e4:
        r1 = 0;
        r2 = "Id";
        r1 = r7.getAttributeValue(r1, r2);	 Catch:{ Exception -> 0x0103 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x0103 }
        r2 = 0;
        r3 = "CallPromptText";
        r2 = r7.getAttributeValue(r2, r3);	 Catch:{ Exception -> 0x0103 }
        if (r1 < 0) goto L_0x011c;
    L_0x00f8:
        r3 = 2;
        if (r1 > r3) goto L_0x011c;
    L_0x00fb:
        r3 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r3 = r3.mCallPromptId;	 Catch:{ Exception -> 0x0103 }
        r3[r1] = r2;	 Catch:{ Exception -> 0x0103 }
        goto L_0x006d;
    L_0x0103:
        r0 = move-exception;
        r1 = "CDMA";
        r2 = "Got exception while loading ERI file.";
        android.telephony.Rlog.e(r1, r2, r0);	 Catch:{ all -> 0x013c }
        r0 = r7 instanceof android.content.res.XmlResourceParser;
        if (r0 == 0) goto L_0x0114;
    L_0x010f:
        r7 = (android.content.res.XmlResourceParser) r7;
        r7.close();
    L_0x0114:
        if (r9 == 0) goto L_0x00c1;
    L_0x0116:
        r9.close();	 Catch:{ IOException -> 0x011a }
        goto L_0x00c1;
    L_0x011a:
        r0 = move-exception;
        goto L_0x00c1;
    L_0x011c:
        r2 = "CDMA";
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0103 }
        r3.<init>();	 Catch:{ Exception -> 0x0103 }
        r4 = "Error Parsing ERI file: found";
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x0103 }
        r1 = r3.append(r1);	 Catch:{ Exception -> 0x0103 }
        r3 = " CallPromptId";
        r1 = r1.append(r3);	 Catch:{ Exception -> 0x0103 }
        r1 = r1.toString();	 Catch:{ Exception -> 0x0103 }
        android.telephony.Rlog.e(r2, r1);	 Catch:{ Exception -> 0x0103 }
        goto L_0x006d;
    L_0x013c:
        r0 = move-exception;
        r1 = r7 instanceof android.content.res.XmlResourceParser;
        if (r1 == 0) goto L_0x0146;
    L_0x0141:
        r7 = (android.content.res.XmlResourceParser) r7;
        r7.close();
    L_0x0146:
        if (r9 == 0) goto L_0x014b;
    L_0x0148:
        r9.close();	 Catch:{ IOException -> 0x01aa }
    L_0x014b:
        throw r0;
    L_0x014c:
        r2 = "EriInfo";
        r1 = r1.equals(r2);	 Catch:{ Exception -> 0x0103 }
        if (r1 == 0) goto L_0x006d;
    L_0x0154:
        r1 = 0;
        r2 = "RoamingIndicator";
        r1 = r7.getAttributeValue(r1, r2);	 Catch:{ Exception -> 0x0103 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x0103 }
        r2 = 0;
        r3 = "IconIndex";
        r2 = r7.getAttributeValue(r2, r3);	 Catch:{ Exception -> 0x0103 }
        r2 = java.lang.Integer.parseInt(r2);	 Catch:{ Exception -> 0x0103 }
        r3 = 0;
        r4 = "IconMode";
        r3 = r7.getAttributeValue(r3, r4);	 Catch:{ Exception -> 0x0103 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ Exception -> 0x0103 }
        r4 = 0;
        r5 = "EriText";
        r4 = r7.getAttributeValue(r4, r5);	 Catch:{ Exception -> 0x0103 }
        r5 = 0;
        r6 = "CallPromptId";
        r5 = r7.getAttributeValue(r5, r6);	 Catch:{ Exception -> 0x0103 }
        r5 = java.lang.Integer.parseInt(r5);	 Catch:{ Exception -> 0x0103 }
        r6 = 0;
        r8 = "AlertId";
        r6 = r7.getAttributeValue(r6, r8);	 Catch:{ Exception -> 0x0103 }
        r6 = java.lang.Integer.parseInt(r6);	 Catch:{ Exception -> 0x0103 }
        r8 = r0 + 1;
        r0 = r12.mEriFile;	 Catch:{ Exception -> 0x0103 }
        r10 = r0.mRoamIndTable;	 Catch:{ Exception -> 0x0103 }
        r11 = java.lang.Integer.valueOf(r1);	 Catch:{ Exception -> 0x0103 }
        r0 = new com.android.internal.telephony.cdma.EriInfo;	 Catch:{ Exception -> 0x0103 }
        r0.<init>(r1, r2, r3, r4, r5, r6);	 Catch:{ Exception -> 0x0103 }
        r10.put(r11, r0);	 Catch:{ Exception -> 0x0103 }
        r0 = r8;
        goto L_0x006d;
    L_0x01a7:
        r0 = move-exception;
        goto L_0x00c1;
    L_0x01aa:
        r1 = move-exception;
        goto L_0x014b;
    L_0x01ac:
        r3 = move-exception;
        goto L_0x00c4;
    L_0x01af:
        r3 = move-exception;
        goto L_0x00d1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.EriManager.loadEriFileFromXml():void");
    }

    public void dispose() {
        this.mEriFile = new EriFile();
        this.mIsEriFileLoaded = false;
    }

    public int getCdmaEriIconIndex(int i, int i2) {
        return getEriDisplayInformation(i, i2).mEriIconIndex;
    }

    public int getCdmaEriIconMode(int i, int i2) {
        return getEriDisplayInformation(i, i2).mEriIconMode;
    }

    public String getCdmaEriText(int i, int i2) {
        return getEriDisplayInformation(i, i2).mEriIconText;
    }

    public int getEriFileType() {
        return this.mEriFile.mEriFileType;
    }

    public int getEriFileVersion() {
        return this.mEriFile.mVersionNumber;
    }

    public int getEriNumberOfEntries() {
        return this.mEriFile.mNumberOfEriEntries;
    }

    public boolean isEriFileLoaded() {
        return this.mIsEriFileLoaded;
    }

    public void loadEriFile() {
        switch (this.mEriFileSource) {
            case 1:
                loadEriFileFromFileSystem();
                return;
            case 2:
                loadEriFileFromModem();
                return;
            default:
                loadEriFileFromXml();
                return;
        }
    }
}
