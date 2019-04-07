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
import com.android.internal.telephony.IIccPhoneBook.Stub;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import java.util.List;

public class IccProvider extends ContentProvider {
    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[]{"name", STR_NUMBER, STR_EMAILS, STR_ANRS, "_id"};
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
    private static final UriMatcher URL_MATCHER = new UriMatcher(-1);
    private SubscriptionManager mSubscriptionManager;

    static {
        URL_MATCHER.addURI("icc", "adn", 1);
        URL_MATCHER.addURI("icc", "adn/subId/#", 2);
        URL_MATCHER.addURI("icc", "fdn", 3);
        URL_MATCHER.addURI("icc", "fdn/subId/#", 4);
        URL_MATCHER.addURI("icc", "sdn", 5);
        URL_MATCHER.addURI("icc", "sdn/subId/#", 6);
        URL_MATCHER.addURI("icc", "adn/adn_all", 7);
    }

    private int getRequestSubId(Uri uri) {
        try {
            return Integer.parseInt(uri.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    private Cursor loadAllSimContacts(int i) {
        Cursor[] cursorArr;
        List activeSubscriptionInfoList = this.mSubscriptionManager.getActiveSubscriptionInfoList();
        if (activeSubscriptionInfoList == null || activeSubscriptionInfoList.size() == 0) {
            cursorArr = new Cursor[0];
        } else {
            int size = activeSubscriptionInfoList.size();
            Cursor[] cursorArr2 = new Cursor[size];
            for (int i2 = 0; i2 < size; i2++) {
                int subscriptionId = ((SubscriptionInfo) activeSubscriptionInfoList.get(i2)).getSubscriptionId();
                cursorArr2[i2] = loadFromEf(i, subscriptionId);
                Rlog.i(TAG, "ADN Records loaded for Subscription ::" + subscriptionId);
            }
            cursorArr = cursorArr2;
        }
        return new MergeCursor(cursorArr);
    }

    private MatrixCursor loadFromEf(int i, int i2) {
        List adnRecordsInEfForSubscriber;
        try {
            IIccPhoneBook asInterface = Stub.asInterface(ServiceManager.getService("simphonebook"));
            adnRecordsInEfForSubscriber = asInterface != null ? asInterface.getAdnRecordsInEfForSubscriber(i2, i) : null;
        } catch (RemoteException e) {
            adnRecordsInEfForSubscriber = null;
        } catch (SecurityException e2) {
            adnRecordsInEfForSubscriber = null;
        }
        if (adnRecordsInEfForSubscriber != null) {
            int size = adnRecordsInEfForSubscriber.size();
            MatrixCursor matrixCursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, size);
            log("adnRecords.size=" + size);
            for (int i3 = 0; i3 < size; i3++) {
                loadRecord((AdnRecord) adnRecordsInEfForSubscriber.get(i3), matrixCursor, i3);
            }
            return matrixCursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    private void loadRecord(AdnRecord adnRecord, MatrixCursor matrixCursor, int i) {
        int i2 = 0;
        if (!adnRecord.isEmpty()) {
            r2 = new Object[5];
            String alphaTag = adnRecord.getAlphaTag();
            String number = adnRecord.getNumber();
            String[] additionalNumbers = adnRecord.getAdditionalNumbers();
            r2[0] = alphaTag;
            r2[1] = number;
            String[] emails = adnRecord.getEmails();
            if (emails != null) {
                StringBuilder stringBuilder = new StringBuilder();
                for (String append : emails) {
                    stringBuilder.append(append);
                    stringBuilder.append(",");
                }
                r2[2] = stringBuilder.toString();
            }
            if (additionalNumbers != null) {
                StringBuilder stringBuilder2 = new StringBuilder();
                int length = additionalNumbers.length;
                while (i2 < length) {
                    stringBuilder2.append(additionalNumbers[i2]);
                    stringBuilder2.append(",");
                    i2++;
                }
                r2[3] = stringBuilder2.toString();
            }
            r2[4] = Integer.valueOf(i);
            matrixCursor.addRow(r2);
        }
    }

    private void log(String str) {
        Rlog.d(TAG, "[IccProvider] " + str);
    }

    private String normalizeValue(String str) {
        int length = str.length();
        return (length != 0 && str.charAt(0) == '\'' && str.charAt(length - 1) == '\'') ? str.substring(1, length - 1) : str;
    }

    public static String[] splitIgnoreSinglequotString(String str, String str2) {
        StringBuilder stringBuilder = new StringBuilder(str);
        if (str2.indexOf(39) != -1) {
            return new String[]{new String(str)};
        }
        int i;
        int indexOf;
        int i2 = 0;
        int indexOf2;
        do {
            indexOf2 = str.indexOf(39, i2);
            i = indexOf2 + 1;
            int indexOf3;
            do {
                indexOf = str.indexOf(39, i);
                indexOf3 = str.indexOf("''", i);
                i = indexOf + 2;
                if (indexOf == -1) {
                    break;
                }
            } while (indexOf == indexOf3);
            if (!(indexOf == -1 || indexOf2 == -1)) {
                for (i2 = indexOf2 + 1; i2 < indexOf; i2++) {
                    stringBuilder.setCharAt(i2, '\'');
                }
                i2 = indexOf + 1;
            }
            if (indexOf == -1) {
                break;
            }
        } while (indexOf2 != -1);
        String[] split = stringBuilder.toString().split(str2);
        indexOf = split.length;
        String[] strArr = new String[indexOf];
        i = 0;
        int i3 = 0;
        while (i3 < indexOf) {
            int length = split[i3].length() + i;
            strArr[i3] = str.substring(i, length);
            i3++;
            i = length + str2.length();
        }
        return strArr;
    }

    private boolean updateIccRecordInEf(int i, ContentValues contentValues, String str, int i2) {
        try {
            IIccPhoneBook asInterface = Stub.asInterface(ServiceManager.getService("simphonebook"));
            return asInterface != null ? asInterface.updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(i2, i, contentValues, str) : false;
        } catch (RemoteException | SecurityException e) {
            return false;
        }
    }

    public int delete(Uri uri, String str, String[] strArr) {
        int i;
        int defaultSubId;
        switch (URL_MATCHER.match(uri)) {
            case 1:
                i = 28474;
                defaultSubId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                i = 28474;
                defaultSubId = getRequestSubId(uri);
                break;
            case 3:
                i = IccConstants.EF_FDN;
                defaultSubId = SubscriptionManager.getDefaultSubId();
                break;
            case 4:
                i = IccConstants.EF_FDN;
                defaultSubId = getRequestSubId(uri);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + uri);
        }
        String str2 = null;
        String str3 = null;
        String str4 = null;
        CharSequence charSequence = null;
        String[] splitIgnoreSinglequotString = TelBrand.IS_SBM ? splitIgnoreSinglequotString(str, "AND") : str.split("AND");
        int length = splitIgnoreSinglequotString.length;
        String str5 = null;
        while (true) {
            int i2 = length - 1;
            if (i2 >= 0) {
                String str6 = splitIgnoreSinglequotString[i2];
                String[] splitIgnoreSinglequotString2 = TelBrand.IS_SBM ? splitIgnoreSinglequotString(str6, "=") : str6.split("=", 2);
                if (splitIgnoreSinglequotString2.length != 2) {
                    Rlog.e(TAG, "resolve: bad whereClause parameter: " + str6);
                    length = i2;
                } else {
                    str6 = splitIgnoreSinglequotString2[0].trim();
                    String trim = splitIgnoreSinglequotString2[1].trim();
                    if (STR_TAG.equals(str6)) {
                        if (TelBrand.IS_SBM) {
                            str2 = normalizeValue(trim).replaceAll("''", "'");
                            length = i2;
                        } else {
                            str2 = normalizeValue(trim);
                            length = i2;
                        }
                    } else if (STR_NUMBER.equals(str6)) {
                        str3 = normalizeValue(trim);
                        length = i2;
                    } else if (STR_EMAILS.equals(str6)) {
                        str4 = normalizeValue(trim);
                        length = i2;
                    } else if (STR_ANRS.equals(str6)) {
                        str5 = normalizeValue(trim);
                        length = i2;
                    } else if (STR_PIN2.equals(str6)) {
                        charSequence = normalizeValue(trim);
                        length = i2;
                    } else {
                        length = i2;
                    }
                }
            } else {
                ContentValues contentValues = new ContentValues();
                contentValues.put(STR_TAG, str2);
                contentValues.put(STR_NUMBER, str3);
                contentValues.put(STR_EMAILS, str4);
                contentValues.put(STR_ANRS, str5);
                contentValues.put(STR_NEW_TAG, "");
                contentValues.put(STR_NEW_NUMBER, "");
                contentValues.put(STR_NEW_EMAILS, "");
                contentValues.put(STR_NEW_ANRS, "");
                if (i == 3 && TextUtils.isEmpty(charSequence)) {
                    return 0;
                }
                if (!updateIccRecordInEf(i, contentValues, charSequence, defaultSubId)) {
                    return 0;
                }
                getContext().getContentResolver().notifyChange(uri, null);
                return 1;
            }
        }
    }

    public String getType(Uri uri) {
        switch (URL_MATCHER.match(uri)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return "vnd.android.cursor.dir/sim-contact";
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    public Uri insert(Uri uri, ContentValues contentValues) {
        int defaultSubId;
        String str;
        int i = 28474;
        int match = URL_MATCHER.match(uri);
        switch (match) {
            case 1:
                defaultSubId = SubscriptionManager.getDefaultSubId();
                str = null;
                break;
            case 2:
                defaultSubId = getRequestSubId(uri);
                str = null;
                break;
            case 3:
                defaultSubId = SubscriptionManager.getDefaultSubId();
                str = contentValues.getAsString(STR_PIN2);
                i = IccConstants.EF_FDN;
                break;
            case 4:
                defaultSubId = getRequestSubId(uri);
                str = contentValues.getAsString(STR_PIN2);
                i = IccConstants.EF_FDN;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + uri);
        }
        String asString = contentValues.getAsString(STR_TAG);
        String asString2 = contentValues.getAsString(STR_NUMBER);
        String asString3 = contentValues.getAsString(STR_EMAILS);
        String asString4 = contentValues.getAsString(STR_ANRS);
        ContentValues contentValues2 = new ContentValues();
        contentValues2.put(STR_TAG, "");
        contentValues2.put(STR_NUMBER, "");
        contentValues2.put(STR_EMAILS, "");
        contentValues2.put(STR_ANRS, "");
        contentValues2.put(STR_NEW_TAG, asString);
        contentValues2.put(STR_NEW_NUMBER, asString2);
        contentValues2.put(STR_NEW_EMAILS, asString3);
        contentValues2.put(STR_NEW_ANRS, asString4);
        if (!updateIccRecordInEf(i, contentValues2, str, defaultSubId)) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder("content://icc/");
        switch (match) {
            case 1:
                stringBuilder.append("adn/");
                break;
            case 2:
                stringBuilder.append("adn/subId/");
                break;
            case 3:
                stringBuilder.append("fdn/");
                break;
            case 4:
                stringBuilder.append("fdn/subId/");
                break;
        }
        stringBuilder.append(0);
        Uri parse = Uri.parse(stringBuilder.toString());
        getContext().getContentResolver().notifyChange(uri, null);
        return parse;
    }

    public boolean onCreate() {
        this.mSubscriptionManager = SubscriptionManager.from(getContext());
        return true;
    }

    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        switch (URL_MATCHER.match(uri)) {
            case 1:
                return loadFromEf(28474, SubscriptionManager.getDefaultSubId());
            case 2:
                return loadFromEf(28474, getRequestSubId(uri));
            case 3:
                return loadFromEf(IccConstants.EF_FDN, SubscriptionManager.getDefaultSubId());
            case 4:
                return loadFromEf(IccConstants.EF_FDN, getRequestSubId(uri));
            case 5:
                return loadFromEf(IccConstants.EF_SDN, SubscriptionManager.getDefaultSubId());
            case 6:
                return loadFromEf(IccConstants.EF_SDN, getRequestSubId(uri));
            case 7:
                return loadAllSimContacts(28474);
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        int defaultSubId;
        String str2;
        int i = 28474;
        switch (URL_MATCHER.match(uri)) {
            case 1:
                defaultSubId = SubscriptionManager.getDefaultSubId();
                str2 = null;
                break;
            case 2:
                defaultSubId = getRequestSubId(uri);
                str2 = null;
                break;
            case 3:
                defaultSubId = SubscriptionManager.getDefaultSubId();
                str2 = contentValues.getAsString(STR_PIN2);
                i = IccConstants.EF_FDN;
                break;
            case 4:
                defaultSubId = getRequestSubId(uri);
                str2 = contentValues.getAsString(STR_PIN2);
                i = IccConstants.EF_FDN;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + uri);
        }
        contentValues.getAsString(STR_TAG);
        contentValues.getAsString(STR_NUMBER);
        contentValues.getAsString(STR_NEW_TAG);
        contentValues.getAsString(STR_NEW_NUMBER);
        if (!updateIccRecordInEf(i, contentValues, str2, defaultSubId)) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return 1;
    }
}
