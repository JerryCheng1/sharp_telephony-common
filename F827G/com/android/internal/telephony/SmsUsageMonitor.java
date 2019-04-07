package com.android.internal.telephony;

import android.app.AppGlobals;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.util.AtomicFile;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class SmsUsageMonitor {
    private static final String ATTR_COUNTRY = "country";
    private static final String ATTR_FREE = "free";
    private static final String ATTR_PACKAGE_NAME = "name";
    private static final String ATTR_PACKAGE_SMS_POLICY = "sms-policy";
    private static final String ATTR_PATTERN = "pattern";
    private static final String ATTR_PREMIUM = "premium";
    private static final String ATTR_STANDARD = "standard";
    static final int CATEGORY_FREE_SHORT_CODE = 1;
    static final int CATEGORY_NOT_SHORT_CODE = 0;
    static final int CATEGORY_POSSIBLE_PREMIUM_SHORT_CODE = 3;
    static final int CATEGORY_PREMIUM_SHORT_CODE = 4;
    static final int CATEGORY_STANDARD_SHORT_CODE = 2;
    private static final boolean DBG = false;
    private static final int DEFAULT_SMS_CHECK_PERIOD = 60000;
    private static final int DEFAULT_SMS_MAX_COUNT = 30;
    public static final int PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW = 3;
    public static final int PREMIUM_SMS_PERMISSION_ASK_USER = 1;
    public static final int PREMIUM_SMS_PERMISSION_NEVER_ALLOW = 2;
    public static final int PREMIUM_SMS_PERMISSION_UNKNOWN = 0;
    private static final String SHORT_CODE_PATH = "/data/misc/sms/codes";
    private static final String SMS_POLICY_FILE_DIRECTORY = "/data/misc/sms";
    private static final String SMS_POLICY_FILE_NAME = "premium_sms_policy.xml";
    private static final String TAG = "SmsUsageMonitor";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_SHORTCODE = "shortcode";
    private static final String TAG_SHORTCODES = "shortcodes";
    private static final String TAG_SMS_POLICY_BODY = "premium-sms-policy";
    private static final boolean VDBG = false;
    private final AtomicBoolean mCheckEnabled = new AtomicBoolean(true);
    private final int mCheckPeriod;
    private final Context mContext;
    private String mCurrentCountry;
    private ShortCodePatternMatcher mCurrentPatternMatcher;
    private final int mMaxAllowed;
    private final File mPatternFile = new File(SHORT_CODE_PATH);
    private long mPatternFileLastModified = 0;
    private AtomicFile mPolicyFile;
    private final HashMap<String, Integer> mPremiumSmsPolicy = new HashMap();
    private final SettingsObserverHandler mSettingsObserverHandler;
    private final HashMap<String, ArrayList<Long>> mSmsStamp = new HashMap();

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicBoolean mEnabled;

        SettingsObserver(Handler handler, Context context, AtomicBoolean atomicBoolean) {
            super(handler);
            this.mContext = context;
            this.mEnabled = atomicBoolean;
            onChange(false);
        }

        public void onChange(boolean z) {
            boolean z2 = true;
            AtomicBoolean atomicBoolean = this.mEnabled;
            if (Global.getInt(this.mContext.getContentResolver(), "sms_short_code_confirmation", 1) == 0) {
                z2 = false;
            }
            atomicBoolean.set(z2);
        }
    }

    private static class SettingsObserverHandler extends Handler {
        SettingsObserverHandler(Context context, AtomicBoolean atomicBoolean) {
            context.getContentResolver().registerContentObserver(Global.getUriFor("sms_short_code_confirmation"), false, new SettingsObserver(this, context, atomicBoolean));
        }
    }

    private static final class ShortCodePatternMatcher {
        private final Pattern mFreeShortCodePattern;
        private final Pattern mPremiumShortCodePattern;
        private final Pattern mShortCodePattern;
        private final Pattern mStandardShortCodePattern;

        ShortCodePatternMatcher(String str, String str2, String str3, String str4) {
            Pattern pattern = null;
            this.mShortCodePattern = str != null ? Pattern.compile(str) : null;
            this.mPremiumShortCodePattern = str2 != null ? Pattern.compile(str2) : null;
            this.mFreeShortCodePattern = str3 != null ? Pattern.compile(str3) : null;
            if (str4 != null) {
                pattern = Pattern.compile(str4);
            }
            this.mStandardShortCodePattern = pattern;
        }

        /* Access modifiers changed, original: 0000 */
        public int getNumberCategory(String str) {
            return (this.mFreeShortCodePattern == null || !this.mFreeShortCodePattern.matcher(str).matches()) ? (this.mStandardShortCodePattern == null || !this.mStandardShortCodePattern.matcher(str).matches()) ? (this.mPremiumShortCodePattern == null || !this.mPremiumShortCodePattern.matcher(str).matches()) ? (this.mShortCodePattern == null || !this.mShortCodePattern.matcher(str).matches()) ? 0 : 3 : 4 : 2 : 1;
        }
    }

    public SmsUsageMonitor(Context context) {
        this.mContext = context;
        ContentResolver contentResolver = context.getContentResolver();
        this.mMaxAllowed = Global.getInt(contentResolver, "sms_outgoing_check_max_count", 30);
        this.mCheckPeriod = Global.getInt(contentResolver, "sms_outgoing_check_interval_ms", 60000);
        this.mSettingsObserverHandler = new SettingsObserverHandler(this.mContext, this.mCheckEnabled);
        loadPremiumSmsPolicyDb();
    }

    private static void checkCallerIsSystemOrPhoneApp() {
        int callingUid = Binder.getCallingUid();
        int appId = UserHandle.getAppId(callingUid);
        if (appId != 1000 && appId != CallFailCause.CDMA_DROP && callingUid != 0) {
            throw new SecurityException("Disallowed call for uid " + callingUid);
        }
    }

    private static void checkCallerIsSystemOrSameApp(String str) {
        int callingUid = Binder.getCallingUid();
        if (UserHandle.getAppId(callingUid) != 1000 && callingUid != 0) {
            try {
                ApplicationInfo applicationInfo = AppGlobals.getPackageManager().getApplicationInfo(str, 0, UserHandle.getCallingUserId());
                if (!UserHandle.isSameApp(applicationInfo.uid, callingUid)) {
                    throw new SecurityException("Calling uid " + callingUid + " gave package" + str + " which is owned by uid " + applicationInfo.uid);
                }
            } catch (RemoteException e) {
                throw new SecurityException("Unknown package " + str + "\n" + e);
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x005f A:{SYNTHETIC, Splitter:B:31:0x005f} */
    /* JADX WARNING: Removed duplicated region for block: B:16:0x0034 A:{SYNTHETIC, Splitter:B:16:0x0034} */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x004e A:{SYNTHETIC, Splitter:B:25:0x004e} */
    /* JADX WARNING: Removed duplicated region for block: B:31:0x005f A:{SYNTHETIC, Splitter:B:31:0x005f} */
    /* JADX WARNING: Removed duplicated region for block: B:31:0x005f A:{SYNTHETIC, Splitter:B:31:0x005f} */
    private com.android.internal.telephony.SmsUsageMonitor.ShortCodePatternMatcher getPatternMatcherFromFile(java.lang.String r7) {
        /*
        r6 = this;
        r3 = 0;
        r0 = new java.io.FileReader;	 Catch:{ FileNotFoundException -> 0x0021, XmlPullParserException -> 0x003a, all -> 0x006c }
        r1 = r6.mPatternFile;	 Catch:{ FileNotFoundException -> 0x0021, XmlPullParserException -> 0x003a, all -> 0x006c }
        r0.<init>(r1);	 Catch:{ FileNotFoundException -> 0x0021, XmlPullParserException -> 0x003a, all -> 0x006c }
        r1 = android.util.Xml.newPullParser();	 Catch:{ FileNotFoundException -> 0x006a, XmlPullParserException -> 0x0063, all -> 0x0054 }
        r1.setInput(r0);	 Catch:{ FileNotFoundException -> 0x006a, XmlPullParserException -> 0x0063, all -> 0x0054 }
        r3 = r6.getPatternMatcherFromXmlParser(r1, r7);	 Catch:{ FileNotFoundException -> 0x006a, XmlPullParserException -> 0x0063, all -> 0x0054 }
        r1 = r6.mPatternFile;
        r4 = r1.lastModified();
        r6.mPatternFileLastModified = r4;
        if (r0 == 0) goto L_0x0020;
    L_0x001d:
        r0.close();	 Catch:{ IOException -> 0x0066 }
    L_0x0020:
        return r3;
    L_0x0021:
        r0 = move-exception;
        r0 = r3;
    L_0x0023:
        r1 = "SmsUsageMonitor";
        r2 = "Short Code Pattern File not found";
        android.telephony.Rlog.e(r1, r2);	 Catch:{ all -> 0x0072 }
        r1 = r6.mPatternFile;
        r4 = r1.lastModified();
        r6.mPatternFileLastModified = r4;
        if (r0 == 0) goto L_0x0020;
    L_0x0034:
        r0.close();	 Catch:{ IOException -> 0x0038 }
        goto L_0x0020;
    L_0x0038:
        r0 = move-exception;
        goto L_0x0020;
    L_0x003a:
        r0 = move-exception;
        r1 = r0;
        r2 = r3;
    L_0x003d:
        r0 = "SmsUsageMonitor";
        r4 = "XML parser exception reading short code pattern file";
        android.telephony.Rlog.e(r0, r4, r1);	 Catch:{ all -> 0x006f }
        r0 = r6.mPatternFile;
        r0 = r0.lastModified();
        r6.mPatternFileLastModified = r0;
        if (r2 == 0) goto L_0x0020;
    L_0x004e:
        r2.close();	 Catch:{ IOException -> 0x0052 }
        goto L_0x0020;
    L_0x0052:
        r0 = move-exception;
        goto L_0x0020;
    L_0x0054:
        r1 = move-exception;
    L_0x0055:
        r2 = r6.mPatternFile;
        r2 = r2.lastModified();
        r6.mPatternFileLastModified = r2;
        if (r0 == 0) goto L_0x0062;
    L_0x005f:
        r0.close();	 Catch:{ IOException -> 0x0068 }
    L_0x0062:
        throw r1;
    L_0x0063:
        r1 = move-exception;
        r2 = r0;
        goto L_0x003d;
    L_0x0066:
        r0 = move-exception;
        goto L_0x0020;
    L_0x0068:
        r0 = move-exception;
        goto L_0x0062;
    L_0x006a:
        r1 = move-exception;
        goto L_0x0023;
    L_0x006c:
        r1 = move-exception;
        r0 = r3;
        goto L_0x0055;
    L_0x006f:
        r1 = move-exception;
        r0 = r2;
        goto L_0x0055;
    L_0x0072:
        r1 = move-exception;
        goto L_0x0055;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsUsageMonitor.getPatternMatcherFromFile(java.lang.String):com.android.internal.telephony.SmsUsageMonitor$ShortCodePatternMatcher");
    }

    private ShortCodePatternMatcher getPatternMatcherFromResource(String str) {
        XmlResourceParser xmlResourceParser = null;
        try {
            xmlResourceParser = this.mContext.getResources().getXml(17891345);
            ShortCodePatternMatcher patternMatcherFromXmlParser = getPatternMatcherFromXmlParser(xmlResourceParser, str);
            return patternMatcherFromXmlParser;
        } finally {
            if (xmlResourceParser != null) {
                xmlResourceParser.close();
            }
        }
    }

    private ShortCodePatternMatcher getPatternMatcherFromXmlParser(XmlPullParser xmlPullParser, String str) {
        try {
            XmlUtils.beginDocument(xmlPullParser, TAG_SHORTCODES);
            while (true) {
                XmlUtils.nextElement(xmlPullParser);
                String name = xmlPullParser.getName();
                if (name == null) {
                    Rlog.e(TAG, "Parsing pattern data found null");
                    break;
                } else if (!name.equals(TAG_SHORTCODE)) {
                    Rlog.e(TAG, "Error: skipping unknown XML tag " + name);
                } else if (str.equals(xmlPullParser.getAttributeValue(null, ATTR_COUNTRY))) {
                    return new ShortCodePatternMatcher(xmlPullParser.getAttributeValue(null, ATTR_PATTERN), xmlPullParser.getAttributeValue(null, ATTR_PREMIUM), xmlPullParser.getAttributeValue(null, ATTR_FREE), xmlPullParser.getAttributeValue(null, ATTR_STANDARD));
                }
            }
        } catch (XmlPullParserException e) {
            Rlog.e(TAG, "XML parser exception reading short code patterns", e);
        } catch (IOException e2) {
            Rlog.e(TAG, "I/O exception reading short code patterns", e2);
        }
        return null;
    }

    private boolean isUnderLimit(ArrayList<Long> arrayList, int i) {
        int i2 = 0;
        Long valueOf = Long.valueOf(System.currentTimeMillis());
        long longValue = valueOf.longValue();
        long j = (long) this.mCheckPeriod;
        while (!arrayList.isEmpty() && ((Long) arrayList.get(0)).longValue() < longValue - j) {
            arrayList.remove(0);
        }
        if (arrayList.size() + i > this.mMaxAllowed) {
            return false;
        }
        while (i2 < i) {
            arrayList.add(valueOf);
            i2++;
        }
        return true;
    }

    /* JADX WARNING: Removed duplicated region for block: B:23:0x0065 A:{ExcHandler: FileNotFoundException (e java.io.FileNotFoundException), PHI: r0 , Splitter:B:6:0x0022} */
    /* JADX WARNING: Removed duplicated region for block: B:32:0x0078 A:{ExcHandler: IOException (r1_6 'e' java.io.IOException), PHI: r0 , Splitter:B:6:0x0022} */
    /* JADX WARNING: Removed duplicated region for block: B:56:0x00da A:{ExcHandler: XmlPullParserException (r1_8 'e' org.xmlpull.v1.XmlPullParserException), PHI: r0 , Splitter:B:6:0x0022} */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Missing block: B:24:0x0066, code skipped:
            if (r0 != null) goto L_0x0068;
     */
    /* JADX WARNING: Missing block: B:26:?, code skipped:
            r0.close();
     */
    /* JADX WARNING: Missing block: B:32:0x0078, code skipped:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:34:?, code skipped:
            android.telephony.Rlog.e(TAG, "Unable to read premium SMS policy database", r1);
     */
    /* JADX WARNING: Missing block: B:35:0x0080, code skipped:
            if (r0 != null) goto L_0x0082;
     */
    /* JADX WARNING: Missing block: B:37:?, code skipped:
            r0.close();
     */
    /* JADX WARNING: Missing block: B:46:0x00b0, code skipped:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:48:?, code skipped:
            android.telephony.Rlog.e(TAG, "Unable to parse premium SMS policy database", r1);
     */
    /* JADX WARNING: Missing block: B:49:0x00b8, code skipped:
            if (r0 != null) goto L_0x00ba;
     */
    /* JADX WARNING: Missing block: B:51:?, code skipped:
            r0.close();
     */
    /* JADX WARNING: Missing block: B:56:0x00da, code skipped:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:58:?, code skipped:
            android.telephony.Rlog.e(TAG, "Unable to parse premium SMS policy database", r1);
     */
    /* JADX WARNING: Missing block: B:59:0x00e2, code skipped:
            if (r0 != null) goto L_0x00e4;
     */
    /* JADX WARNING: Missing block: B:61:?, code skipped:
            r0.close();
     */
    private void loadPremiumSmsPolicyDb() {
        /*
        r7 = this;
        r0 = 0;
        r2 = r7.mPremiumSmsPolicy;
        monitor-enter(r2);
        r1 = r7.mPolicyFile;	 Catch:{ all -> 0x00f3 }
        if (r1 != 0) goto L_0x0043;
    L_0x0008:
        r1 = new android.util.AtomicFile;	 Catch:{ all -> 0x00f3 }
        r3 = new java.io.File;	 Catch:{ all -> 0x00f3 }
        r4 = new java.io.File;	 Catch:{ all -> 0x00f3 }
        r5 = "/data/misc/sms";
        r4.<init>(r5);	 Catch:{ all -> 0x00f3 }
        r5 = "premium_sms_policy.xml";
        r3.<init>(r4, r5);	 Catch:{ all -> 0x00f3 }
        r1.<init>(r3);	 Catch:{ all -> 0x00f3 }
        r7.mPolicyFile = r1;	 Catch:{ all -> 0x00f3 }
        r1 = r7.mPremiumSmsPolicy;	 Catch:{ all -> 0x00f3 }
        r1.clear();	 Catch:{ all -> 0x00f3 }
        r1 = r7.mPolicyFile;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r0 = r1.openRead();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r1 = android.util.Xml.newPullParser();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = 0;
        r1.setInput(r0, r3);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = "premium-sms-policy";
        com.android.internal.util.XmlUtils.beginDocument(r1, r3);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
    L_0x0035:
        com.android.internal.util.XmlUtils.nextElement(r1);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = r1.getName();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r3 != 0) goto L_0x0045;
    L_0x003e:
        if (r0 == 0) goto L_0x0043;
    L_0x0040:
        r0.close();	 Catch:{ IOException -> 0x00f6 }
    L_0x0043:
        monitor-exit(r2);	 Catch:{ all -> 0x00f3 }
        return;
    L_0x0045:
        r4 = "package";
        r4 = r3.equals(r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r4 == 0) goto L_0x00c0;
    L_0x004d:
        r3 = 0;
        r4 = "name";
        r3 = r1.getAttributeValue(r3, r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r4 = 0;
        r5 = "sms-policy";
        r4 = r1.getAttributeValue(r4, r5);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r3 != 0) goto L_0x006e;
    L_0x005d:
        r3 = "SmsUsageMonitor";
        r4 = "Error: missing package name attribute";
        android.telephony.Rlog.e(r3, r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0065:
        r1 = move-exception;
        if (r0 == 0) goto L_0x0043;
    L_0x0068:
        r0.close();	 Catch:{ IOException -> 0x006c }
        goto L_0x0043;
    L_0x006c:
        r0 = move-exception;
        goto L_0x0043;
    L_0x006e:
        if (r4 != 0) goto L_0x0088;
    L_0x0070:
        r3 = "SmsUsageMonitor";
        r4 = "Error: missing package policy attribute";
        android.telephony.Rlog.e(r3, r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0078:
        r1 = move-exception;
        r3 = "SmsUsageMonitor";
        r4 = "Unable to read premium SMS policy database";
        android.telephony.Rlog.e(r3, r4, r1);	 Catch:{ all -> 0x00ec }
        if (r0 == 0) goto L_0x0043;
    L_0x0082:
        r0.close();	 Catch:{ IOException -> 0x0086 }
        goto L_0x0043;
    L_0x0086:
        r0 = move-exception;
        goto L_0x0043;
    L_0x0088:
        r5 = r7.mPremiumSmsPolicy;	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r6 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r5.put(r3, r6);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0096:
        r3 = move-exception;
        r3 = "SmsUsageMonitor";
        r5 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r5.<init>();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r6 = "Error: non-numeric policy type ";
        r5 = r5.append(r6);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r4 = r5.append(r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r4 = r4.toString();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        android.telephony.Rlog.e(r3, r4);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x00b0:
        r1 = move-exception;
        r3 = "SmsUsageMonitor";
        r4 = "Unable to parse premium SMS policy database";
        android.telephony.Rlog.e(r3, r4, r1);	 Catch:{ all -> 0x00ec }
        if (r0 == 0) goto L_0x0043;
    L_0x00ba:
        r0.close();	 Catch:{ IOException -> 0x00be }
        goto L_0x0043;
    L_0x00be:
        r0 = move-exception;
        goto L_0x0043;
    L_0x00c0:
        r4 = "SmsUsageMonitor";
        r5 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r5.<init>();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r6 = "Error: skipping unknown XML tag ";
        r5 = r5.append(r6);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = r5.append(r3);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = r3.toString();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        android.telephony.Rlog.e(r4, r3);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x00da:
        r1 = move-exception;
        r3 = "SmsUsageMonitor";
        r4 = "Unable to parse premium SMS policy database";
        android.telephony.Rlog.e(r3, r4, r1);	 Catch:{ all -> 0x00ec }
        if (r0 == 0) goto L_0x0043;
    L_0x00e4:
        r0.close();	 Catch:{ IOException -> 0x00e9 }
        goto L_0x0043;
    L_0x00e9:
        r0 = move-exception;
        goto L_0x0043;
    L_0x00ec:
        r1 = move-exception;
        if (r0 == 0) goto L_0x00f2;
    L_0x00ef:
        r0.close();	 Catch:{ IOException -> 0x00f9 }
    L_0x00f2:
        throw r1;	 Catch:{ all -> 0x00f3 }
    L_0x00f3:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x00f3 }
        throw r0;
    L_0x00f6:
        r0 = move-exception;
        goto L_0x0043;
    L_0x00f9:
        r0 = move-exception;
        goto L_0x00f2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsUsageMonitor.loadPremiumSmsPolicyDb():void");
    }

    private static void log(String str) {
        Rlog.d(TAG, str);
    }

    public static int mergeShortCodeCategories(int i, int i2) {
        return i > i2 ? i : i2;
    }

    private void removeExpiredTimestamps() {
        long currentTimeMillis = System.currentTimeMillis();
        long j = (long) this.mCheckPeriod;
        synchronized (this.mSmsStamp) {
            Iterator it = this.mSmsStamp.entrySet().iterator();
            while (it.hasNext()) {
                ArrayList arrayList = (ArrayList) ((Entry) it.next()).getValue();
                if (arrayList.isEmpty() || ((Long) arrayList.get(arrayList.size() - 1)).longValue() < currentTimeMillis - j) {
                    it.remove();
                }
            }
        }
    }

    private void writePremiumSmsPolicyDb() {
        Throwable e;
        FileOutputStream fileOutputStream = null;
        synchronized (this.mPremiumSmsPolicy) {
            try {
                FileOutputStream startWrite = this.mPolicyFile.startWrite();
                try {
                    FastXmlSerializer fastXmlSerializer = new FastXmlSerializer();
                    fastXmlSerializer.setOutput(startWrite, "utf-8");
                    fastXmlSerializer.startDocument(null, Boolean.valueOf(true));
                    fastXmlSerializer.startTag(null, TAG_SMS_POLICY_BODY);
                    for (Entry entry : this.mPremiumSmsPolicy.entrySet()) {
                        fastXmlSerializer.startTag(null, "package");
                        fastXmlSerializer.attribute(null, "name", (String) entry.getKey());
                        fastXmlSerializer.attribute(null, ATTR_PACKAGE_SMS_POLICY, ((Integer) entry.getValue()).toString());
                        fastXmlSerializer.endTag(null, "package");
                    }
                    fastXmlSerializer.endTag(null, TAG_SMS_POLICY_BODY);
                    fastXmlSerializer.endDocument();
                    this.mPolicyFile.finishWrite(startWrite);
                } catch (IOException e2) {
                    e = e2;
                    fileOutputStream = startWrite;
                }
            } catch (IOException e3) {
                e = e3;
            }
        }
        Rlog.e(TAG, "Unable to write premium SMS policy database", e);
        if (fileOutputStream != null) {
            this.mPolicyFile.failWrite(fileOutputStream);
        }
    }

    public boolean check(String str, int i) {
        boolean isUnderLimit;
        synchronized (this.mSmsStamp) {
            removeExpiredTimestamps();
            ArrayList arrayList = (ArrayList) this.mSmsStamp.get(str);
            if (arrayList == null) {
                arrayList = new ArrayList();
                this.mSmsStamp.put(str, arrayList);
            }
            isUnderLimit = isUnderLimit(arrayList, i);
        }
        return isUnderLimit;
    }

    public int checkDestination(String str, String str2) {
        synchronized (this.mSettingsObserverHandler) {
            if (PhoneNumberUtils.isEmergencyNumber(str, str2)) {
                return 0;
            } else if (this.mCheckEnabled.get()) {
                if (str2 != null) {
                    if (!(this.mCurrentCountry != null && str2.equals(this.mCurrentCountry) && this.mPatternFile.lastModified() == this.mPatternFileLastModified)) {
                        if (this.mPatternFile.exists()) {
                            this.mCurrentPatternMatcher = getPatternMatcherFromFile(str2);
                        } else {
                            this.mCurrentPatternMatcher = getPatternMatcherFromResource(str2);
                        }
                        this.mCurrentCountry = str2;
                    }
                }
                if (this.mCurrentPatternMatcher != null) {
                    int numberCategory = this.mCurrentPatternMatcher.getNumberCategory(str);
                    return numberCategory;
                }
                Rlog.e(TAG, "No patterns for \"" + str2 + "\": using generic short code rule");
                if (str.length() <= 5) {
                    return 3;
                }
                return 0;
            } else {
                return 0;
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        this.mSmsStamp.clear();
    }

    public int getPremiumSmsPermission(String str) {
        checkCallerIsSystemOrSameApp(str);
        synchronized (this.mPremiumSmsPolicy) {
            Integer num = (Integer) this.mPremiumSmsPolicy.get(str);
            if (num == null) {
                return 0;
            }
            int intValue = num.intValue();
            return intValue;
        }
    }

    public void setPremiumSmsPermission(String str, int i) {
        checkCallerIsSystemOrPhoneApp();
        if (i < 1 || i > 3) {
            throw new IllegalArgumentException("invalid SMS permission type " + i);
        }
        synchronized (this.mPremiumSmsPolicy) {
            this.mPremiumSmsPolicy.put(str, Integer.valueOf(i));
        }
        new Thread(new Runnable() {
            public void run() {
                SmsUsageMonitor.this.writePremiumSmsPolicyDb();
            }
        }).start();
    }
}
