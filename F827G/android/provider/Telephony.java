package android.provider;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.net.Uri.Builder;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Patterns;
import android.util.SeempJavaFilter;
import android.util.SeempLog;
import com.android.internal.telephony.SmsApplication;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Telephony {
    private static final String TAG = "Telephony";

    public interface BaseMmsColumns extends BaseColumns {
        @Deprecated
        public static final String ADAPTATION_ALLOWED = "adp_a";
        @Deprecated
        public static final String APPLIC_ID = "apl_id";
        @Deprecated
        public static final String AUX_APPLIC_ID = "aux_apl_id";
        @Deprecated
        public static final String CANCEL_ID = "cl_id";
        @Deprecated
        public static final String CANCEL_STATUS = "cl_st";
        public static final String CONTENT_CLASS = "ct_cls";
        public static final String CONTENT_LOCATION = "ct_l";
        public static final String CONTENT_TYPE = "ct_t";
        public static final String CREATOR = "creator";
        public static final String DATE = "date";
        public static final String DATE_SENT = "date_sent";
        public static final String DELIVERY_REPORT = "d_rpt";
        public static final String DELIVERY_TIME = "d_tm";
        @Deprecated
        public static final String DELIVERY_TIME_TOKEN = "d_tm_tok";
        @Deprecated
        public static final String DISTRIBUTION_INDICATOR = "d_ind";
        @Deprecated
        public static final String DRM_CONTENT = "drm_c";
        @Deprecated
        public static final String ELEMENT_DESCRIPTOR = "e_des";
        public static final String EXPIRY = "exp";
        @Deprecated
        public static final String LIMIT = "limit";
        public static final String LOCKED = "locked";
        @Deprecated
        public static final String MBOX_QUOTAS = "mb_qt";
        @Deprecated
        public static final String MBOX_QUOTAS_TOKEN = "mb_qt_tok";
        @Deprecated
        public static final String MBOX_TOTALS = "mb_t";
        @Deprecated
        public static final String MBOX_TOTALS_TOKEN = "mb_t_tok";
        public static final String MESSAGE_BOX = "msg_box";
        public static final int MESSAGE_BOX_ALL = 0;
        public static final int MESSAGE_BOX_DRAFTS = 3;
        public static final int MESSAGE_BOX_FAILED = 5;
        public static final int MESSAGE_BOX_INBOX = 1;
        public static final int MESSAGE_BOX_OUTBOX = 4;
        public static final int MESSAGE_BOX_SENT = 2;
        public static final String MESSAGE_CLASS = "m_cls";
        @Deprecated
        public static final String MESSAGE_COUNT = "m_cnt";
        public static final String MESSAGE_ID = "m_id";
        public static final String MESSAGE_SIZE = "m_size";
        public static final String MESSAGE_TYPE = "m_type";
        public static final String MMS_VERSION = "v";
        @Deprecated
        public static final String MM_FLAGS = "mm_flg";
        @Deprecated
        public static final String MM_FLAGS_TOKEN = "mm_flg_tok";
        @Deprecated
        public static final String MM_STATE = "mm_st";
        public static final String PHONE_ID = "phone_id";
        @Deprecated
        public static final String PREVIOUSLY_SENT_BY = "p_s_by";
        @Deprecated
        public static final String PREVIOUSLY_SENT_DATE = "p_s_d";
        public static final String PRIORITY = "pri";
        @Deprecated
        public static final String QUOTAS = "qt";
        public static final String READ = "read";
        public static final String READ_REPORT = "rr";
        public static final String READ_STATUS = "read_status";
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE = "r_r_mod";
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE_TEXT = "r_r_mod_txt";
        @Deprecated
        public static final String REPLACE_ID = "repl_id";
        @Deprecated
        public static final String REPLY_APPLIC_ID = "r_apl_id";
        @Deprecated
        public static final String REPLY_CHARGING = "r_chg";
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE = "r_chg_dl";
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE_TOKEN = "r_chg_dl_tok";
        @Deprecated
        public static final String REPLY_CHARGING_ID = "r_chg_id";
        @Deprecated
        public static final String REPLY_CHARGING_SIZE = "r_chg_sz";
        public static final String REPORT_ALLOWED = "rpt_a";
        public static final String RESPONSE_STATUS = "resp_st";
        public static final String RESPONSE_TEXT = "resp_txt";
        public static final String RETRIEVE_STATUS = "retr_st";
        public static final String RETRIEVE_TEXT = "retr_txt";
        public static final String RETRIEVE_TEXT_CHARSET = "retr_txt_cs";
        public static final String SEEN = "seen";
        @Deprecated
        public static final String SENDER_VISIBILITY = "s_vis";
        @Deprecated
        public static final String START = "start";
        public static final String STATUS = "st";
        @Deprecated
        public static final String STATUS_TEXT = "st_txt";
        @Deprecated
        public static final String STORE = "store";
        @Deprecated
        public static final String STORED = "stored";
        @Deprecated
        public static final String STORE_STATUS = "store_st";
        @Deprecated
        public static final String STORE_STATUS_TEXT = "store_st_txt";
        public static final String SUBJECT = "sub";
        public static final String SUBJECT_CHARSET = "sub_cs";
        public static final String SUBSCRIPTION_ID = "sub_id";
        public static final String TEXT_ONLY = "text_only";
        public static final String THREAD_ID = "thread_id";
        @Deprecated
        public static final String TOTALS = "totals";
        public static final String TRANSACTION_ID = "tr_id";
    }

    public interface CanonicalAddressesColumns extends BaseColumns {
        public static final String ADDRESS = "address";
    }

    public static final class Carriers implements BaseColumns {
        public static final String APN = "apn";
        public static final String AUTH_TYPE = "authtype";
        public static final String BEARER = "bearer";
        public static final String CARRIER_ENABLED = "carrier_enabled";
        public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
        public static final String CURRENT = "current";
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final String MAX_CONNS = "max_conns";
        public static final String MAX_CONNS_TIME = "max_conns_time";
        public static final String MCC = "mcc";
        public static final String MMSC = "mmsc";
        public static final String MMSPORT = "mmsport";
        public static final String MMSPROXY = "mmsproxy";
        public static final String MNC = "mnc";
        public static final String MODEM_COGNITIVE = "modem_cognitive";
        public static final String MTU = "mtu";
        public static final String MVNO_MATCH_DATA = "mvno_match_data";
        public static final String MVNO_TYPE = "mvno_type";
        public static final String NAME = "name";
        public static final String NUMERIC = "numeric";
        public static final String OEM_DNS_PRIMARY = "oem_dns_primary";
        public static final String OEM_DNS_SECOUNDARY = "oem_dns_secoundary";
        public static final String PASSWORD = "password";
        public static final String PHONE_ID = "phone_id";
        public static final String PORT = "port";
        public static final String PROFILE_ID = "profile_id";
        public static final String PROTOCOL = "protocol";
        public static final String PROXY = "proxy";
        public static final String ROAMING_PROTOCOL = "roaming_protocol";
        public static final String SERVER = "server";
        public static final String SUBSCRIPTION_ID = "sub_id";
        public static final String TYPE = "type";
        public static final String USER = "user";
        public static final String WAIT_TIME = "wait_time";

        private Carriers() {
        }
    }

    public static final class CellBroadcasts implements BaseColumns {
        public static final String CID = "cid";
        public static final String CMAS_CATEGORY = "cmas_category";
        public static final String CMAS_CERTAINTY = "cmas_certainty";
        public static final String CMAS_MESSAGE_CLASS = "cmas_message_class";
        public static final String CMAS_RESPONSE_TYPE = "cmas_response_type";
        public static final String CMAS_SEVERITY = "cmas_severity";
        public static final String CMAS_URGENCY = "cmas_urgency";
        public static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts");
        public static final String DEFAULT_SORT_ORDER = "date DESC";
        public static final String DELIVERY_TIME = "date";
        public static final String ETWS_WARNING_TYPE = "etws_warning_type";
        public static final String GEOGRAPHICAL_SCOPE = "geo_scope";
        public static final String LAC = "lac";
        public static final String LANGUAGE_CODE = "language";
        public static final String MESSAGE_BODY = "body";
        public static final String MESSAGE_FORMAT = "format";
        public static final String MESSAGE_PRIORITY = "priority";
        public static final String MESSAGE_READ = "read";
        public static final String PLMN = "plmn";
        public static final String[] QUERY_COLUMNS = new String[]{"_id", GEOGRAPHICAL_SCOPE, PLMN, LAC, "cid", SERIAL_NUMBER, SERVICE_CATEGORY, LANGUAGE_CODE, "body", "date", "read", MESSAGE_FORMAT, "priority", ETWS_WARNING_TYPE, CMAS_MESSAGE_CLASS, CMAS_CATEGORY, CMAS_RESPONSE_TYPE, CMAS_SEVERITY, CMAS_URGENCY, CMAS_CERTAINTY};
        public static final String SERIAL_NUMBER = "serial_number";
        public static final String SERVICE_CATEGORY = "service_category";
        public static final String V1_MESSAGE_CODE = "message_code";
        public static final String V1_MESSAGE_IDENTIFIER = "message_id";

        private CellBroadcasts() {
        }
    }

    public static final class Mms implements BaseMmsColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://mms");
        public static final String DEFAULT_SORT_ORDER = "date DESC";
        public static final Pattern NAME_ADDR_EMAIL_PATTERN = Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");
        public static final Uri REPORT_REQUEST_URI = Uri.withAppendedPath(CONTENT_URI, "report-request");
        public static final Uri REPORT_STATUS_URI = Uri.withAppendedPath(CONTENT_URI, "report-status");

        public static final class Addr implements BaseColumns {
            public static final String ADDRESS = "address";
            public static final String CHARSET = "charset";
            public static final String CONTACT_ID = "contact_id";
            public static final String MSG_ID = "msg_id";
            public static final String TYPE = "type";

            private Addr() {
            }
        }

        public static final class Draft implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/drafts");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Draft() {
            }
        }

        public static final class Inbox implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Inbox() {
            }
        }

        public static final class Intents {
            public static final String CONTENT_CHANGED_ACTION = "android.intent.action.CONTENT_CHANGED";
            public static final String DELETED_CONTENTS = "deleted_contents";
            public static final String EXTRA_MMS_CONTENT_URI = "android.provider.Telephony.extra.MMS_CONTENT_URI";
            public static final String EXTRA_MMS_LOCATION_URL = "android.provider.Telephony.extra.MMS_LOCATION_URL";
            public static final String MMS_DOWNLOAD_ACTION = "android.provider.Telephony.MMS_DOWNLOAD";
            public static final String MMS_SEND_ACTION = "android.provider.Telephony.MMS_SEND";

            private Intents() {
            }
        }

        public static final class Outbox implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Outbox() {
            }
        }

        public static final class Part implements BaseColumns {
            public static final String CHARSET = "chset";
            public static final String CONTENT_DISPOSITION = "cd";
            public static final String CONTENT_ID = "cid";
            public static final String CONTENT_LOCATION = "cl";
            public static final String CONTENT_TYPE = "ct";
            public static final String CT_START = "ctt_s";
            public static final String CT_TYPE = "ctt_t";
            public static final String FILENAME = "fn";
            public static final String MSG_ID = "mid";
            public static final String NAME = "name";
            public static final String SEQ = "seq";
            public static final String TEXT = "text";
            public static final String _DATA = "_data";

            private Part() {
            }
        }

        public static final class Rate {
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "rate");
            public static final String SENT_TIME = "sent_time";

            private Rate() {
            }
        }

        public static final class Sent implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Sent() {
            }
        }

        private Mms() {
        }

        public static String extractAddrSpec(String str) {
            Matcher matcher = NAME_ADDR_EMAIL_PATTERN.matcher(str);
            return matcher.matches() ? matcher.group(2) : str;
        }

        public static boolean isEmailAddress(String str) {
            if (TextUtils.isEmpty(str)) {
                return false;
            }
            return Patterns.EMAIL_ADDRESS.matcher(extractAddrSpec(str)).matches();
        }

        public static boolean isPhoneNumber(String str) {
            return TextUtils.isEmpty(str) ? false : Patterns.PHONE.matcher(str).matches();
        }

        public static Cursor query(ContentResolver contentResolver, String[] strArr) {
            if (SeempJavaFilter.check(Telephony.TAG, "query").booleanValue()) {
                SeempLog.record("Telephony|query|--end");
            }
            return contentResolver.query(CONTENT_URI, strArr, null, null, "date DESC");
        }

        public static Cursor query(ContentResolver contentResolver, String[] strArr, String str, String str2) {
            if (SeempJavaFilter.check(Telephony.TAG, "query").booleanValue()) {
                SeempLog.record("Telephony|query|where," + (str == null ? "null" : str) + " " + "orderBy," + (str2 == null ? "null" : str2) + "|--end");
            }
            return contentResolver.query(CONTENT_URI, strArr, str, null, str2 == null ? "date DESC" : str2);
        }
    }

    public static final class MmsSms implements BaseColumns {
        public static final Uri CONTENT_CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations");
        public static final Uri CONTENT_DRAFT_URI = Uri.parse("content://mms-sms/draft");
        public static final Uri CONTENT_FILTER_BYPHONE_URI = Uri.parse("content://mms-sms/messages/byphone");
        public static final Uri CONTENT_LOCKED_URI = Uri.parse("content://mms-sms/locked");
        public static final Uri CONTENT_UNDELIVERED_URI = Uri.parse("content://mms-sms/undelivered");
        public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");
        public static final int ERR_TYPE_GENERIC = 1;
        public static final int ERR_TYPE_GENERIC_PERMANENT = 10;
        public static final int ERR_TYPE_MMS_PROTO_PERMANENT = 12;
        public static final int ERR_TYPE_MMS_PROTO_TRANSIENT = 3;
        public static final int ERR_TYPE_SMS_PROTO_PERMANENT = 11;
        public static final int ERR_TYPE_SMS_PROTO_TRANSIENT = 2;
        public static final int ERR_TYPE_TRANSPORT_FAILURE = 4;
        public static final int MMS_PROTO = 1;
        public static final int NO_ERROR = 0;
        public static final Uri SEARCH_URI = Uri.parse("content://mms-sms/search");
        public static final int SMS_PROTO = 0;
        public static final String TYPE_DISCRIMINATOR_COLUMN = "transport_type";

        public static final class PendingMessages implements BaseColumns {
            public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "pending");
            public static final String DUE_TIME = "due_time";
            public static final String ERROR_CODE = "err_code";
            public static final String ERROR_TYPE = "err_type";
            public static final String LAST_TRY = "last_try";
            public static final String MSG_ID = "msg_id";
            public static final String MSG_TYPE = "msg_type";
            public static final String PHONE_ID = "pending_phone_id";
            public static final String PROTO_TYPE = "proto_type";
            public static final String RETRY_INDEX = "retry_index";
            public static final String SUBSCRIPTION_ID = "pending_sub_id";

            private PendingMessages() {
            }
        }

        public static final class WordsTable {
            public static final String ID = "_id";
            public static final String INDEXED_TEXT = "index_text";
            public static final String SOURCE_ROW_ID = "source_id";
            public static final String TABLE_ID = "table_to_use";

            private WordsTable() {
            }
        }

        private MmsSms() {
        }
    }

    public interface TextBasedSmsColumns {
        public static final String ADDRESS = "address";
        public static final String BODY = "body";
        public static final String CREATOR = "creator";
        public static final String DATE = "date";
        public static final String DATE_SENT = "date_sent";
        public static final String ERROR_CODE = "error_code";
        public static final String LOCKED = "locked";
        public static final int MESSAGE_TYPE_ALL = 0;
        public static final int MESSAGE_TYPE_DRAFT = 3;
        public static final int MESSAGE_TYPE_FAILED = 5;
        public static final int MESSAGE_TYPE_INBOX = 1;
        public static final int MESSAGE_TYPE_OUTBOX = 4;
        public static final int MESSAGE_TYPE_QUEUED = 6;
        public static final int MESSAGE_TYPE_SENT = 2;
        public static final String MTU = "mtu";
        public static final String PERSON = "person";
        public static final String PHONE_ID = "phone_id";
        public static final String PRIORITY = "priority";
        public static final String PROTOCOL = "protocol";
        public static final String READ = "read";
        public static final String REPLY_PATH_PRESENT = "reply_path_present";
        public static final String SEEN = "seen";
        public static final String SERVICE_CENTER = "service_center";
        public static final String STATUS = "status";
        public static final int STATUS_COMPLETE = 0;
        public static final int STATUS_FAILED = 64;
        public static final int STATUS_NONE = -1;
        public static final int STATUS_PENDING = 32;
        public static final String SUBJECT = "subject";
        public static final String SUBSCRIPTION_ID = "sub_id";
        public static final String THREAD_ID = "thread_id";
        public static final String TYPE = "type";
    }

    public static final class Sms implements BaseColumns, TextBasedSmsColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://sms");
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        public static final class Conversations implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/conversations");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
            public static final String MESSAGE_COUNT = "msg_count";
            public static final String SNIPPET = "snippet";

            private Conversations() {
            }
        }

        public static final class Draft implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/draft");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Draft() {
            }

            public static Uri addMessage(int i, ContentResolver contentResolver, String str, String str2, String str3, Long l) {
                return Sms.addMessageToUri(i, contentResolver, CONTENT_URI, str, str2, str3, l, true, false);
            }

            public static Uri addMessage(ContentResolver contentResolver, String str, String str2, String str3, Long l) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, CONTENT_URI, str, str2, str3, l, true, false);
            }
        }

        public static final class Inbox implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Inbox() {
            }

            public static Uri addMessage(int i, ContentResolver contentResolver, String str, String str2, String str3, Long l, boolean z) {
                return Sms.addMessageToUri(i, contentResolver, CONTENT_URI, str, str2, str3, l, z, false);
            }

            public static Uri addMessage(ContentResolver contentResolver, String str, String str2, String str3, Long l, boolean z) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, CONTENT_URI, str, str2, str3, l, z, false);
            }
        }

        public static final class Intents {
            public static final String ACTION_CHANGE_DEFAULT = "android.provider.Telephony.ACTION_CHANGE_DEFAULT";
            public static final String DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED";
            public static final String EXTRA_PACKAGE_NAME = "package";
            public static final String MMS_DOWNLOADED_ACTION = "android.provider.Telephony.MMS_DOWNLOADED";
            public static final int RESULT_SMS_DUPLICATED = 5;
            public static final int RESULT_SMS_GENERIC_ERROR = 2;
            public static final int RESULT_SMS_HANDLED = 1;
            public static final int RESULT_SMS_OUT_OF_MEMORY = 3;
            public static final int RESULT_SMS_UNSUPPORTED = 4;
            public static final String SIM_FULL_ACTION = "android.provider.Telephony.SIM_FULL";
            public static final String SMS_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_CB_RECEIVED";
            public static final String SMS_DELIVER_ACTION = "android.provider.Telephony.SMS_DELIVER";
            public static final String SMS_EMERGENCY_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED";
            public static final String SMS_FILTER_ACTION = "android.provider.Telephony.SMS_FILTER";
            public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
            public static final String SMS_REJECTED_ACTION = "android.provider.Telephony.SMS_REJECTED";
            public static final String SMS_SEND_ACTION = "android.provider.Telephony.SMS_SEND";
            public static final String SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION = "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED";
            public static final String WAP_PUSH_DELIVER_ACTION = "android.provider.Telephony.WAP_PUSH_DELIVER";
            public static final String WAP_PUSH_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_RECEIVED";

            private Intents() {
            }

            public static SmsMessage[] getMessagesFromIntent(Intent intent) {
                Object[] objArr = (Object[]) intent.getSerializableExtra("pdus");
                String stringExtra = intent.getStringExtra(CellBroadcasts.MESSAGE_FORMAT);
                int intExtra = intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubId());
                Rlog.v(Telephony.TAG, " getMessagesFromIntent sub_id : " + intExtra);
                int length = objArr.length;
                SmsMessage[] smsMessageArr = new SmsMessage[length];
                for (int i = 0; i < length; i++) {
                    smsMessageArr[i] = SmsMessage.createFromPdu((byte[]) objArr[i], stringExtra);
                    smsMessageArr[i].setSubId(intExtra);
                }
                return smsMessageArr;
            }
        }

        public static final class Outbox implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Outbox() {
            }

            public static Uri addMessage(int i, ContentResolver contentResolver, String str, String str2, String str3, Long l, boolean z, long j) {
                return Sms.addMessageToUri(i, contentResolver, CONTENT_URI, str, str2, str3, l, true, z, j);
            }

            public static Uri addMessage(ContentResolver contentResolver, String str, String str2, String str3, Long l, boolean z, long j) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, CONTENT_URI, str, str2, str3, l, true, z, j);
            }
        }

        public static final class Sent implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Sent() {
            }

            public static Uri addMessage(int i, ContentResolver contentResolver, String str, String str2, String str3, Long l) {
                return Sms.addMessageToUri(i, contentResolver, CONTENT_URI, str, str2, str3, l, true, false);
            }

            public static Uri addMessage(ContentResolver contentResolver, String str, String str2, String str3, Long l) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, CONTENT_URI, str, str2, str3, l, true, false);
            }
        }

        private Sms() {
        }

        public static Uri addMessageToUri(int i, ContentResolver contentResolver, Uri uri, String str, String str2, String str3, Long l, boolean z, boolean z2) {
            return addMessageToUri(i, contentResolver, uri, str, str2, str3, l, z, z2, -1);
        }

        public static Uri addMessageToUri(int i, ContentResolver contentResolver, Uri uri, String str, String str2, String str3, Long l, boolean z, boolean z2, long j) {
            return addMessageToUri(i, contentResolver, uri, str, str2, str3, l, z, z2, j, -1);
        }

        public static Uri addMessageToUri(int i, ContentResolver contentResolver, Uri uri, String str, String str2, String str3, Long l, boolean z, boolean z2, long j, int i2) {
            ContentValues contentValues = new ContentValues(9);
            Rlog.v(Telephony.TAG, "Telephony addMessageToUri sub id: " + i);
            int phoneId = SubscriptionManager.getPhoneId(i);
            contentValues.put("sub_id", Integer.valueOf(i));
            contentValues.put("phone_id", Integer.valueOf(phoneId));
            contentValues.put("address", str);
            if (l != null) {
                contentValues.put("date", l);
            }
            contentValues.put("read", z ? Integer.valueOf(1) : Integer.valueOf(0));
            contentValues.put(TextBasedSmsColumns.SUBJECT, str3);
            contentValues.put("body", str2);
            contentValues.put("priority", Integer.valueOf(i2));
            if (z2) {
                contentValues.put(TextBasedSmsColumns.STATUS, Integer.valueOf(32));
            }
            if (j != -1) {
                contentValues.put("thread_id", Long.valueOf(j));
            }
            return contentResolver.insert(uri, contentValues);
        }

        public static Uri addMessageToUri(ContentResolver contentResolver, Uri uri, String str, String str2, String str3, Long l, boolean z, boolean z2) {
            return addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, uri, str, str2, str3, l, z, z2, -1);
        }

        public static Uri addMessageToUri(ContentResolver contentResolver, Uri uri, String str, String str2, String str3, Long l, boolean z, boolean z2, long j) {
            return addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), contentResolver, uri, str, str2, str3, l, z, z2, j);
        }

        public static String getDefaultSmsPackage(Context context) {
            ComponentName defaultSmsApplication = SmsApplication.getDefaultSmsApplication(context, false);
            return defaultSmsApplication != null ? defaultSmsApplication.getPackageName() : null;
        }

        public static boolean isOutgoingFolder(int i) {
            return i == 5 || i == 4 || i == 2 || i == 6;
        }

        public static boolean moveMessageToFolder(Context context, Uri uri, int i, int i2) {
            boolean z = true;
            if (uri == null) {
                return false;
            }
            int i3;
            int i4;
            switch (i) {
                case 1:
                case 3:
                    i3 = 0;
                    i4 = 0;
                    break;
                case 2:
                case 4:
                    i3 = 0;
                    i4 = 1;
                    break;
                case 5:
                case 6:
                    i3 = 1;
                    i4 = 0;
                    break;
                default:
                    return false;
            }
            ContentValues contentValues = new ContentValues(3);
            contentValues.put("type", Integer.valueOf(i));
            if (i3 != 0) {
                contentValues.put("read", Integer.valueOf(0));
            } else if (i4 != 0) {
                contentValues.put("read", Integer.valueOf(1));
            }
            contentValues.put(TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(i2));
            if (1 != SqliteWrapper.update(context, context.getContentResolver(), uri, contentValues, null, null)) {
                z = false;
            }
            return z;
        }

        public static Cursor query(ContentResolver contentResolver, String[] strArr) {
            if (SeempJavaFilter.check(Telephony.TAG, "query").booleanValue()) {
                SeempLog.record("Telephony|query|--end");
            }
            return contentResolver.query(CONTENT_URI, strArr, null, null, "date DESC");
        }

        public static Cursor query(ContentResolver contentResolver, String[] strArr, String str, String str2) {
            if (SeempJavaFilter.check(Telephony.TAG, "query").booleanValue()) {
                SeempLog.record("Telephony|query|where," + (str == null ? "null" : str) + " " + "orderBy," + (str2 == null ? "null" : str2) + "|--end");
            }
            return contentResolver.query(CONTENT_URI, strArr, str, null, str2 == null ? "date DESC" : str2);
        }
    }

    public interface ThreadsColumns extends BaseColumns {
        public static final String ARCHIVED = "archived";
        public static final String DATE = "date";
        public static final String ERROR = "error";
        public static final String HAS_ATTACHMENT = "has_attachment";
        public static final String MESSAGE_COUNT = "message_count";
        public static final String READ = "read";
        public static final String RECIPIENT_IDS = "recipient_ids";
        public static final String SNIPPET = "snippet";
        public static final String SNIPPET_CHARSET = "snippet_cs";
        public static final String TYPE = "type";
    }

    public static final class Threads implements ThreadsColumns {
        public static final int BROADCAST_THREAD = 1;
        public static final int COMMON_THREAD = 0;
        public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "conversations");
        private static final String[] ID_PROJECTION = new String[]{"_id"};
        public static final Uri OBSOLETE_THREADS_URI = Uri.withAppendedPath(CONTENT_URI, "obsolete");
        private static final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/threadID");

        private Threads() {
        }

        public static long getOrCreateThreadId(Context context, String str) {
            Set hashSet = new HashSet();
            hashSet.add(str);
            return getOrCreateThreadId(context, hashSet);
        }

        public static long getOrCreateThreadId(Context context, Set<String> set) {
            Builder buildUpon = THREAD_ID_CONTENT_URI.buildUpon();
            for (String str : set) {
                String str2;
                if (Mms.isEmailAddress(str2)) {
                    str2 = Mms.extractAddrSpec(str2);
                }
                buildUpon.appendQueryParameter("recipient", str2);
            }
            long build = buildUpon.build();
            Cursor query = SqliteWrapper.query(context, context.getContentResolver(), build, ID_PROJECTION, null, null, null);
            if (query != null) {
                try {
                    if (query.moveToFirst()) {
                        build = query.getLong(0);
                        return build;
                    }
                    build = "getOrCreateThreadId returned no rows!";
                    Rlog.e(Telephony.TAG, build);
                    query.close();
                } finally {
                    query.close();
                }
            }
            Rlog.e(Telephony.TAG, "getOrCreateThreadId failed with " + set.size() + " recipients");
            throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
        }
    }

    private Telephony() {
    }
}
