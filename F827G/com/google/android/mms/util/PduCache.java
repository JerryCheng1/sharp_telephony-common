package com.google.android.mms.util;

import android.content.ContentUris;
import android.content.UriMatcher;
import android.net.Uri;
import android.provider.Telephony.Mms;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public final class PduCache extends AbstractCache<Uri, PduCacheEntry> {
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;
    private static final HashMap<Integer, Integer> MATCH_TO_MSGBOX_ID_MAP = new HashMap();
    private static final int MMS_ALL = 0;
    private static final int MMS_ALL_ID = 1;
    private static final int MMS_CONVERSATION = 10;
    private static final int MMS_CONVERSATION_ID = 11;
    private static final int MMS_DRAFTS = 6;
    private static final int MMS_DRAFTS_ID = 7;
    private static final int MMS_INBOX = 2;
    private static final int MMS_INBOX_ID = 3;
    private static final int MMS_OUTBOX = 8;
    private static final int MMS_OUTBOX_ID = 9;
    private static final int MMS_SENT = 4;
    private static final int MMS_SENT_ID = 5;
    private static final String TAG = "PduCache";
    private static final UriMatcher URI_MATCHER = new UriMatcher(-1);
    private static PduCache sInstance;
    private final HashMap<Integer, HashSet<Uri>> mMessageBoxes = new HashMap();
    private final HashMap<Long, HashSet<Uri>> mThreads = new HashMap();
    private final HashSet<Uri> mUpdating = new HashSet();

    static {
        URI_MATCHER.addURI("mms", null, 0);
        URI_MATCHER.addURI("mms", "#", 1);
        URI_MATCHER.addURI("mms", "inbox", 2);
        URI_MATCHER.addURI("mms", "inbox/#", 3);
        URI_MATCHER.addURI("mms", "sent", 4);
        URI_MATCHER.addURI("mms", "sent/#", 5);
        URI_MATCHER.addURI("mms", "drafts", 6);
        URI_MATCHER.addURI("mms", "drafts/#", 7);
        URI_MATCHER.addURI("mms", "outbox", 8);
        URI_MATCHER.addURI("mms", "outbox/#", 9);
        URI_MATCHER.addURI("mms-sms", "conversations", 10);
        URI_MATCHER.addURI("mms-sms", "conversations/#", 11);
        MATCH_TO_MSGBOX_ID_MAP.put(Integer.valueOf(2), Integer.valueOf(1));
        MATCH_TO_MSGBOX_ID_MAP.put(Integer.valueOf(4), Integer.valueOf(2));
        MATCH_TO_MSGBOX_ID_MAP.put(Integer.valueOf(6), Integer.valueOf(3));
        MATCH_TO_MSGBOX_ID_MAP.put(Integer.valueOf(8), Integer.valueOf(4));
    }

    private PduCache() {
    }

    public static final PduCache getInstance() {
        synchronized (PduCache.class) {
            try {
                if (sInstance == null) {
                    sInstance = new PduCache();
                }
                PduCache pduCache = sInstance;
                return pduCache;
            } finally {
                Object obj = PduCache.class;
            }
        }
    }

    private Uri normalizeKey(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case 1:
                return uri;
            case 3:
            case 5:
            case 7:
            case 9:
                return Uri.withAppendedPath(Mms.CONTENT_URI, uri.getLastPathSegment());
            default:
                return null;
        }
    }

    private void purgeByMessageBox(Integer num) {
        if (num != null) {
            HashSet hashSet = (HashSet) this.mMessageBoxes.remove(num);
            if (hashSet != null) {
                Iterator it = hashSet.iterator();
                while (it.hasNext()) {
                    Uri uri = (Uri) it.next();
                    this.mUpdating.remove(uri);
                    PduCacheEntry pduCacheEntry = (PduCacheEntry) super.purge(uri);
                    if (pduCacheEntry != null) {
                        removeFromThreads(uri, pduCacheEntry);
                    }
                }
            }
        }
    }

    private void purgeByThreadId(long j) {
        HashSet hashSet = (HashSet) this.mThreads.remove(Long.valueOf(j));
        if (hashSet != null) {
            Iterator it = hashSet.iterator();
            while (it.hasNext()) {
                Uri uri = (Uri) it.next();
                this.mUpdating.remove(uri);
                PduCacheEntry pduCacheEntry = (PduCacheEntry) super.purge(uri);
                if (pduCacheEntry != null) {
                    removeFromMessageBoxes(uri, pduCacheEntry);
                }
            }
        }
    }

    private PduCacheEntry purgeSingleEntry(Uri uri) {
        this.mUpdating.remove(uri);
        PduCacheEntry pduCacheEntry = (PduCacheEntry) super.purge(uri);
        if (pduCacheEntry == null) {
            return null;
        }
        removeFromThreads(uri, pduCacheEntry);
        removeFromMessageBoxes(uri, pduCacheEntry);
        return pduCacheEntry;
    }

    private void removeFromMessageBoxes(Uri uri, PduCacheEntry pduCacheEntry) {
        HashSet hashSet = (HashSet) this.mThreads.get(Long.valueOf((long) pduCacheEntry.getMessageBox()));
        if (hashSet != null) {
            hashSet.remove(uri);
        }
    }

    private void removeFromThreads(Uri uri, PduCacheEntry pduCacheEntry) {
        HashSet hashSet = (HashSet) this.mThreads.get(Long.valueOf(pduCacheEntry.getThreadId()));
        if (hashSet != null) {
            hashSet.remove(uri);
        }
    }

    public boolean isUpdating(Uri uri) {
        boolean contains;
        synchronized (this) {
            contains = this.mUpdating.contains(uri);
        }
        return contains;
    }

    public PduCacheEntry purge(Uri uri) {
        PduCacheEntry pduCacheEntry;
        synchronized (this) {
            int match = URI_MATCHER.match(uri);
            switch (match) {
                case 0:
                case 10:
                    purgeAll();
                    pduCacheEntry = null;
                    break;
                case 1:
                    pduCacheEntry = purgeSingleEntry(uri);
                    break;
                case 2:
                case 4:
                case 6:
                case 8:
                    purgeByMessageBox((Integer) MATCH_TO_MSGBOX_ID_MAP.get(Integer.valueOf(match)));
                    pduCacheEntry = null;
                    break;
                case 3:
                case 5:
                case 7:
                case 9:
                    pduCacheEntry = purgeSingleEntry(Uri.withAppendedPath(Mms.CONTENT_URI, uri.getLastPathSegment()));
                    break;
                case 11:
                    purgeByThreadId(ContentUris.parseId(uri));
                    pduCacheEntry = null;
                    break;
                default:
                    pduCacheEntry = null;
                    break;
            }
        }
        return pduCacheEntry;
    }

    public void purgeAll() {
        synchronized (this) {
            super.purgeAll();
            this.mMessageBoxes.clear();
            this.mThreads.clear();
            this.mUpdating.clear();
        }
    }

    public boolean put(Uri uri, PduCacheEntry pduCacheEntry) {
        boolean put;
        synchronized (this) {
            HashSet hashSet;
            int messageBox = pduCacheEntry.getMessageBox();
            HashSet hashSet2 = (HashSet) this.mMessageBoxes.get(Integer.valueOf(messageBox));
            if (hashSet2 == null) {
                hashSet2 = new HashSet();
                this.mMessageBoxes.put(Integer.valueOf(messageBox), hashSet2);
                hashSet = hashSet2;
            } else {
                hashSet = hashSet2;
            }
            long threadId = pduCacheEntry.getThreadId();
            hashSet2 = (HashSet) this.mThreads.get(Long.valueOf(threadId));
            if (hashSet2 == null) {
                hashSet2 = new HashSet();
                this.mThreads.put(Long.valueOf(threadId), hashSet2);
            }
            Uri normalizeKey = normalizeKey(uri);
            put = super.put(normalizeKey, pduCacheEntry);
            if (put) {
                hashSet.add(normalizeKey);
                hashSet2.add(normalizeKey);
            }
            setUpdating(uri, false);
        }
        return put;
    }

    public void setUpdating(Uri uri, boolean z) {
        synchronized (this) {
            if (z) {
                this.mUpdating.add(uri);
            } else {
                this.mUpdating.remove(uri);
            }
        }
    }
}
