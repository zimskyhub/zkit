package com.zmtech.zkit.cache.impl;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;

public class ZEntry<K, V> implements Cache.Entry<K, V> {
    private static final Class<ZEntry> thisClass = ZEntry.class;
    private final K key;
    V value;
    private long createdTime = 0;
    long lastUpdatedTime = 0;
    long lastAccessTime = 0;
    long accessCount = 0;
    boolean isExpired = false;

    public ZEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public ZEntry(K key, V value, long createdTime) {
        this.key = key;
        this.value = value;
        this.createdTime = createdTime;
        lastUpdatedTime = createdTime;
        lastAccessTime = createdTime;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) return clazz.cast(this);
        throw new IllegalArgumentException("ZCache缓存错误: 类型: " + clazz.getName() + " 不是 ZCache.ZEntry 类型!");
    }

    boolean valueEquals(V otherValue) {
        if (otherValue == null) {
            return value == null;
        } else {
            return otherValue.equals(value);
        }
    }

    void setValue(V val, long updateTime) {
        synchronized (key) {
            if (updateTime > lastUpdatedTime) {
                value = val;
                lastUpdatedTime = updateTime;
            }
        }
    }

    boolean setValueIfEquals(V oldVal, V val, long updateTime) {
        synchronized (key) {
            if (updateTime > lastUpdatedTime && valueEquals(oldVal)) {
                value = val;
                lastUpdatedTime = updateTime;
                return true;
            } else {
                return false;
            }
        }
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getAccessCount() {
        return accessCount;
    }

    /* done directly on fields for performance reasons
    void countAccess(long accessTime) {
        accessCount++; if (accessTime > lastAccessTime) lastAccessTime = accessTime;
    }
    */
    @SuppressWarnings("unused")
    public boolean isExpired(ExpiryPolicy policy) {
        return isExpired(System.currentTimeMillis(), policy.getExpiryForAccess(), policy.getExpiryForCreation(),
                policy.getExpiryForUpdate());
    }

    boolean isExpired(long accessTime, ExpiryPolicy policy) {
        return isExpired(accessTime, policy.getExpiryForAccess(), policy.getExpiryForCreation(),
                policy.getExpiryForUpdate());
    }

    boolean isExpired(Duration accessDuration, Duration creationDuration, Duration updateDuration) {
        return isExpired(System.currentTimeMillis(), accessDuration, creationDuration, updateDuration);
    }

    boolean isExpired(long accessTime, Duration accessDuration, Duration creationDuration, Duration updateDuration) {
        if (isExpired) return true;
        if (accessDuration != null && !accessDuration.isEternal()) {
            if (accessDuration.getAdjustedTime(lastAccessTime) < accessTime) {
                isExpired = true;
                return true;
            }
        }
        if (creationDuration != null && !creationDuration.isEternal()) {
            if (creationDuration.getAdjustedTime(createdTime) < accessTime) {
                isExpired = true;
                return true;
            }
        }
        if (updateDuration != null && !updateDuration.isEternal()) {
            if (updateDuration.getAdjustedTime(lastUpdatedTime) < accessTime) {
                isExpired = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || thisClass != obj.getClass()) return false;
        ZEntry that = (ZEntry) obj;
        if (value == null) {
            return that.value == null;
        } else {
            return value.equals(that.value);
        }
    }
}
