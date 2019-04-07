package com.google.android.mms.util;

import java.util.HashMap;

public abstract class AbstractCache<K, V> {
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;
    private static final int MAX_CACHED_ITEMS = 500;
    private static final String TAG = "AbstractCache";
    private final HashMap<K, CacheEntry<V>> mCacheMap = new HashMap();

    private static class CacheEntry<V> {
        int hit;
        V value;

        private CacheEntry() {
        }
    }

    protected AbstractCache() {
    }

    public V get(K k) {
        if (k != null) {
            CacheEntry cacheEntry = (CacheEntry) this.mCacheMap.get(k);
            if (cacheEntry != null) {
                cacheEntry.hit++;
                return cacheEntry.value;
            }
        }
        return null;
    }

    public V purge(K k) {
        CacheEntry cacheEntry = (CacheEntry) this.mCacheMap.remove(k);
        return cacheEntry != null ? cacheEntry.value : null;
    }

    public void purgeAll() {
        this.mCacheMap.clear();
    }

    public boolean put(K k, V v) {
        if (this.mCacheMap.size() >= MAX_CACHED_ITEMS || k == null) {
            return false;
        }
        CacheEntry cacheEntry = new CacheEntry();
        cacheEntry.value = v;
        this.mCacheMap.put(k, cacheEntry);
        return true;
    }

    public int size() {
        return this.mCacheMap.size();
    }
}
