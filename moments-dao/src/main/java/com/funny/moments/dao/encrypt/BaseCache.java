package com.funny.moments.dao.encrypt;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;


public abstract class BaseCache<K, V> {


    private volatile int maximumSize = 1000;
    private volatile int expireAfterWriteDuration = 1*60;
    private volatile TimeUnit timeUnit = TimeUnit.SECONDS;
    private volatile LoadingCache<K, V> cache;


    public LoadingCache<K, V> getCache() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = CacheBuilder.newBuilder().maximumSize(maximumSize)
                            .expireAfterWrite(expireAfterWriteDuration, timeUnit)
                            .recordStats()
                            .build(new CacheLoader<K, V>() {
                                @Override
                                public V load(K key) throws Exception {
                                    return fetchData(key);
                                }
                            });
                }
            }
        }
        return cache;
    }


    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }


    public void setExpireAfterWriteDuration(int expireAfterWriteDuration) {
        this.expireAfterWriteDuration = expireAfterWriteDuration;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    protected V getValue(K key) {
        V result = getCache().getUnchecked(key);
        return result;
    }

    protected void refresh(K key){
        getCache().refresh(key);
    }
    protected abstract V fetchData(K key);

}