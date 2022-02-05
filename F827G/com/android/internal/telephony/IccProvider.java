package com.android.internal.telephony;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class IccProvider extends ContentProvider {
    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = null;
    protected static final int ADN = 1;
    protected static final int ADN_ALL = 7;
    protected static final int ADN_SUB = 2;
    private static final boolean DBG = false;
    protected static final int FDN = 3;
    protected static final int FDN_SUB = 4;
    protected static final int SDN = 5;
    protected static final int SDN_SUB = 6;
    public static final String STR_ANRS = "anrs";
    public static final String STR_EMAILS = "emails";
    public static final String STR_NEW_ANRS = "newAnrs";
    public static final String STR_NEW_EMAILS = "newEmails";
    public static final String STR_NEW_NUMBER = "newNumber";
    public static final String STR_NEW_TAG = "newTag";
    public static final String STR_NUMBER = "number";
    public static final String STR_PIN2 = "pin2";
    public static final String STR_TAG = "tag";
    private static final String TAG = "IccProvider";
    private static final UriMatcher URL_MATCHER = null;
    private SubscriptionManager mSubscriptionManager;

    /*  JADX ERROR: Failed to decode insn: 0x000F: UNKNOWN(0x20E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x000F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0027: UNKNOWN(0x30E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0027: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0031: UNKNOWN(0x30E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0031: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0039: UNKNOWN(0x30E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0039: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0050: UNKNOWN(0x30E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0050: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x005E: UNKNOWN(0x10E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x005E: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0064: UNKNOWN(0x20E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0064: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0072: UNKNOWN(0x10E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0072: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x007A: UNKNOWN(0x30E9), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x007A: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0080: UNKNOWN(0x01EA), method: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0080: UNKNOWN(0x01EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    public static java.lang.String[] splitIgnoreSinglequotString(java.lang.String r16, java.lang.String r17) {
        /*
            java.lang.StringBuilder r5 = new java.lang.StringBuilder
            r0 = r16
            r5.<init>(r0)
            r3 = 0
            r7 = 0
            r8 = 0
            r9 = 0
            r14 = 39
            r0 = r17
            // decode failed: Unknown instruction: '0x000F: UNKNOWN(0x20E9)'
            monitor-exit(r0)
            int r0 = r10 << 14
            r15 = -1
            if (r14 == r15) goto L_0x0023
            r14 = 1
            java.lang.String[] r10 = new java.lang.String[r14]
            r14 = 0
            java.lang.String r15 = new java.lang.String
            r15.<init>(r16)
            r10[r14] = r15
            r11 = r10
            return r11
            r14 = 39
            r0 = r16
            // decode failed: Unknown instruction: '0x0027: UNKNOWN(0x30E9)'
            java.util.List r0 = (java.util.List) r0
            r7 = move-result
            int r6 = r7 + 1
            r14 = 39
            r0 = r16
            // decode failed: Unknown instruction: '0x0031: UNKNOWN(0x30E9)'
            L r0 = (L) r0
            r8 = move-result
            java.lang.String r14 = "''"
            r0 = r16
            // decode failed: Unknown instruction: '0x0039: UNKNOWN(0x30E9)'
            int r0 = r0.length
            int r6 = r10 << 9
            int r6 = r8 + 2
            r14 = -1
            if (r8 == r14) goto L_0x0044
            if (r8 == r9) goto L_0x002d
            r14 = -1
            if (r8 == r14) goto L_0x0058
            r14 = -1
            if (r7 == r14) goto L_0x0058
            int r2 = r7 + 1
            if (r2 >= r8) goto L_0x0056
            r14 = 39
            // decode failed: Unknown instruction: '0x0050: UNKNOWN(0x30E9)'
            goto L_0x0e76
            int r2 = r2 + 1
            goto L_0x004c
            int r3 = r8 + 1
            r14 = -1
            if (r8 == r14) goto L_0x005e
            r14 = -1
            if (r7 != r14) goto L_0x0023
            // decode failed: Unknown instruction: '0x005E: UNKNOWN(0x10E9)'
            r0 = r0
            r0 = r3596
            r0 = r17
            // decode failed: Unknown instruction: '0x0064: UNKNOWN(0x20E9)'
            int r0 = (r14 > r0 ? 1 : (r14 == r0 ? 0 : -1))
            r13 = move-result
            int r4 = r13.length
            r12 = 0
            r1 = 0
            java.lang.String[] r10 = new java.lang.String[r4]
            r2 = 0
            if (r2 >= r4) goto L_0x0089
            r14 = r13[r2]
            // decode failed: Unknown instruction: '0x0072: UNKNOWN(0x10E9)'
            goto L_0x0073
            return
            r14 = move-result
            int r1 = r12 + r14
            r0 = r16
            // decode failed: Unknown instruction: '0x007A: UNKNOWN(0x30E9)'
            if (r0 <= r0) goto L_0x023b
            r14 = move-result
            r10[r2] = r14
            // decode failed: Unknown instruction: '0x0080: UNKNOWN(0x01EA)'
            goto L_0x0081
            return r0
            r14 = move-result
            int r12 = r1 + r14
            int r2 = r2 + 1
            goto L_0x006e
            r11 = r10
            goto L_0x0022
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.IccProvider.splitIgnoreSinglequotString(java.lang.String, java.lang.String):java.lang.String[]");
    }

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        this.mSubscriptionManager = SubscriptionManager.from(getContext());
        return true;
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        switch (URL_MATCHER.match(url)) {
            case 1:
                return loadFromEf(28474, SubscriptionManager.getDefaultSubId());
            case 2:
                return loadFromEf(28474, getRequestSubId(url));
            case 3:
                return loadFromEf(IccConstants.EF_FDN, SubscriptionManager.getDefaultSubId());
            case 4:
                return loadFromEf(IccConstants.EF_FDN, getRequestSubId(url));
            case 5:
                return loadFromEf(IccConstants.EF_SDN, SubscriptionManager.getDefaultSubId());
            case 6:
                return loadFromEf(IccConstants.EF_SDN, getRequestSubId(url));
            case 7:
                return loadAllSimContacts(28474);
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    private Cursor loadAllSimContacts(int efType) {
        Cursor[] result;
        List<SubscriptionInfo> subInfoList = this.mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subInfoList == null || subInfoList.size() == 0) {
            result = new Cursor[0];
        } else {
            int subIdCount = subInfoList.size();
            result = new Cursor[subIdCount];
            for (int i = 0; i < subIdCount; i++) {
                int subId = subInfoList.get(i).getSubscriptionId();
                result[i] = loadFromEf(efType, subId);
                Rlog.i(TAG, "ADN Records loaded for Subscription ::" + subId);
            }
        }
        return new MergeCursor(result);
    }

    @Override // android.content.ContentProvider
    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return "vnd.android.cursor.dir/sim-contact";
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri url, ContentValues initialValues) {
        int efType;
        int subId;
        String pin2 = null;
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = initialValues.getAsString(STR_PIN2);
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = initialValues.getAsString(STR_PIN2);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = initialValues.getAsString(STR_TAG);
        String number = initialValues.getAsString(STR_NUMBER);
        String emails = initialValues.getAsString(STR_EMAILS);
        String anrs = initialValues.getAsString(STR_ANRS);
        ContentValues mValues = new ContentValues();
        mValues.put(STR_TAG, "");
        mValues.put(STR_NUMBER, "");
        mValues.put(STR_EMAILS, "");
        mValues.put(STR_ANRS, "");
        mValues.put(STR_NEW_TAG, tag);
        mValues.put(STR_NEW_NUMBER, number);
        mValues.put(STR_NEW_EMAILS, emails);
        mValues.put(STR_NEW_ANRS, anrs);
        if (!updateIccRecordInEf(efType, mValues, pin2, subId)) {
            return null;
        }
        StringBuilder buf = new StringBuilder("content://icc/");
        switch (match) {
            case 1:
                buf.append("adn/");
                break;
            case 2:
                buf.append("adn/subId/");
                break;
            case 3:
                buf.append("fdn/");
                break;
            case 4:
                buf.append("fdn/subId/");
                break;
        }
        buf.append(0);
        Uri parse = Uri.parse(buf.toString());
        getContext().getContentResolver().notifyChange(url, null);
        return parse;
    }

    private String normalizeValue(String inVal) {
        int len = inVal.length();
        if (len == 0) {
            return inVal;
        }
        String retVal = inVal;
        if (inVal.charAt(0) == '\'' && inVal.charAt(len - 1) == '\'') {
            retVal = inVal.substring(1, len - 1);
        }
        return retVal;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri url, String where, String[] whereArgs) {
        int efType;
        int subId;
        String[] tokens;
        String[] pair;
        switch (URL_MATCHER.match(url)) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = null;
        String number = null;
        String emails = null;
        String anrs = null;
        String pin2 = null;
        if (TelBrand.IS_SBM) {
            tokens = splitIgnoreSinglequotString(where, "AND");
        } else {
            tokens = where.split("AND");
        }
        int n = tokens.length;
        while (true) {
            n--;
            if (n >= 0) {
                String param = tokens[n];
                if (TelBrand.IS_SBM) {
                    pair = splitIgnoreSinglequotString(param, "=");
                } else {
                    pair = param.split("=", 2);
                }
                if (pair.length != 2) {
                    Rlog.e(TAG, "resolve: bad whereClause parameter: " + param);
                } else {
                    String key = pair[0].trim();
                    String val = pair[1].trim();
                    if (STR_TAG.equals(key)) {
                        if (TelBrand.IS_SBM) {
                            tag = normalizeValue(val).replaceAll("''", "'");
                        } else {
                            tag = normalizeValue(val);
                        }
                    } else if (STR_NUMBER.equals(key)) {
                        number = normalizeValue(val);
                    } else if (STR_EMAILS.equals(key)) {
                        emails = normalizeValue(val);
                    } else if (STR_ANRS.equals(key)) {
                        anrs = normalizeValue(val);
                    } else if (STR_PIN2.equals(key)) {
                        pin2 = normalizeValue(val);
                    }
                }
            } else {
                ContentValues mValues = new ContentValues();
                mValues.put(STR_TAG, tag);
                mValues.put(STR_NUMBER, number);
                mValues.put(STR_EMAILS, emails);
                mValues.put(STR_ANRS, anrs);
                mValues.put(STR_NEW_TAG, "");
                mValues.put(STR_NEW_NUMBER, "");
                mValues.put(STR_NEW_EMAILS, "");
                mValues.put(STR_NEW_ANRS, "");
                if ((efType == 3 && TextUtils.isEmpty(pin2)) || !updateIccRecordInEf(efType, mValues, pin2, subId)) {
                    return 0;
                }
                getContext().getContentResolver().notifyChange(url, null);
                return 1;
            }
        }
    }

    @Override // android.content.ContentProvider
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int efType;
        int subId;
        String pin2 = null;
        switch (URL_MATCHER.match(url)) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = values.getAsString(STR_PIN2);
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = values.getAsString(STR_PIN2);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        values.getAsString(STR_TAG);
        values.getAsString(STR_NUMBER);
        values.getAsString(STR_NEW_TAG);
        values.getAsString(STR_NEW_NUMBER);
        if (!updateIccRecordInEf(efType, values, pin2, subId)) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(url, null);
        return 1;
    }

    private MatrixCursor loadFromEf(int efType, int subId) {
        List<AdnRecord> adnRecords = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEfForSubscriber(subId, efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException e2) {
        }
        if (adnRecords != null) {
            int N = adnRecords.size();
            MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            log("adnRecords.size=" + N);
            for (int i = 0; i < N; i++) {
                loadRecord(adnRecords.get(i), cursor, i);
            }
            return cursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    private boolean updateIccRecordInEf(int efType, ContentValues values, String pin2, int subId) {
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                return iccIpb.updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(subId, efType, values, pin2);
            }
            return false;
        } catch (RemoteException e) {
            return false;
        } catch (SecurityException e2) {
            return false;
        }
    }

    private void loadRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            Object[] contact = new Object[5];
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();
            String[] anrs = record.getAdditionalNumbers();
            contact[0] = alphaTag;
            contact[1] = number;
            String[] emails = record.getEmails();
            if (emails != null) {
                StringBuilder emailString = new StringBuilder();
                for (String email : emails) {
                    emailString.append(email);
                    emailString.append(",");
                }
                contact[2] = emailString.toString();
            }
            if (anrs != null) {
                StringBuilder anrString = new StringBuilder();
                for (String anr : anrs) {
                    anrString.append(anr);
                    anrString.append(",");
                }
                contact[3] = anrString.toString();
            }
            contact[4] = Integer.valueOf(id);
            cursor.addRow(contact);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, "[IccProvider] " + msg);
    }

    private int getRequestSubId(Uri url) {
        try {
            return Integer.parseInt(url.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown URL " + url);
        }
    }
}
