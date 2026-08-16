package com.dnsfilt.dnsresolver.service;

import com.dnsfilt.dnsresolver.config.AppConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.dnsfilt.dnsresolver.model.DNSResourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * CacheService
 * 
 * Manages L1 In-Memory Caffeine Cache for ultra-low latency (< 0.05ms) DNS query resolution.
 * Implements the Bill Pugh Singleton Pattern.
 */
public class CacheService {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    // Default fallbacks
    private static final long DEFAULT_MAX_SIZE = 10_000L;
    private static final long DEFAULT_TTL_MINUTES = 5L;

    // L1 Caffeine Cache instance
    private final Cache<String, DNSResourceRecord> l1Cache;

    private CacheService() {
        AppConfig config = AppConfig.getInstance();
        
        long maxSize = parseLongConfig(config, "L1_CACHE_MAX_SIZE", "l1.cache.maxSize", DEFAULT_MAX_SIZE);
        long ttlMinutes = parseLongConfig(config, "L1_CACHE_TTL_MINUTES", "l1.cache.ttlMinutes", DEFAULT_TTL_MINUTES);

        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
        logger.info("L1 Caffeine Cache initialized (maxSize={}, ttl={}m)", maxSize, ttlMinutes);
    }

    /**
     * Bill Pugh Singleton Holder
     */
    private static class InstanceHolder {
        private static final CacheService INSTANCE = new CacheService();
    }

    public static CacheService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private long parseLongConfig(AppConfig config, String envKey, String propKey, long defaultValue) {
        String val = config.getEnvVariable(envKey);
        if (val == null || val.trim().isEmpty()) {
            val = config.getEnvVariable(propKey);
        }
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid config value '{}' for {}. Falling back to default {}", val, envKey, defaultValue);
            }
        }
        return defaultValue;
    }

    public DNSResourceRecord getL1(String key) {
        DNSResourceRecord record = l1Cache.getIfPresent(key);
        if (record != null) {
            logger.debug("L1 Cache HIT for key: {}", key);
        }
        return record;
    }

    public void putL1(String key, DNSResourceRecord record) {
        if (key != null && record != null) {
            l1Cache.put(key, record);
            logger.debug("Stored key '{}' in L1 Caffeine Cache", key);
        }
    }

    public void invalidateL1(String key) {
        l1Cache.invalidate(key);
        logger.info("Invalidated L1 Cache key: {}", key);
    }

    public void clearL1() {
        l1Cache.invalidateAll();
        logger.info("Cleared L1 Cache");
    }

    public double getHitRate() {
        return l1Cache.stats().hitRate();
    }
}
