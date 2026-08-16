package com.dnsfilt.dnsadmin.service;

import com.dnsfilt.dnsadmin.config.CaffeineCacheConfig;
import com.dnsfilt.dnsadmin.entity.BlockedEntryEntity;
import com.dnsfilt.dnsadmin.repository.BlockedEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.util.List;

/**
 * RuleService
 * 
 * Manages domain-level security blocklist rules in Oracle ATP Database,
 * maintains Caffeine in-memory read cache, and syncs updates to Redis for the DNS Resolver.
 * 
 * Caching & Sync Architecture:
 * - Read Queries: Cached in Caffeine ("rulesCache") for instant dashboard response.
 * - Write Operations: Automatically evicts the Caffeine cache via @CacheEvict and publishes
 *   the updated domain into the Redis set 'blocked_domains' so dnsfilt-resolver picks it up immediately.
 */
@Service
public class RuleService {
    private static final Logger logger = LoggerFactory.getLogger(RuleService.class);

    private final BlockedEntryRepository repository;
    private JedisPool jedisPool;

    public RuleService(BlockedEntryRepository repository, @Value("${redis.url:redis://localhost:6379}") String redisUrl) {
        this.repository = repository;
        try {
            if (redisUrl != null && !redisUrl.isEmpty()) {
                this.jedisPool = new JedisPool(new URI(redisUrl));
            }
        } catch (Exception e) {
            logger.warn("Redis connection failed for RuleService: {}. Proceeding with DB fallback.", e.getMessage());
        }
    }

    /**
     * Retrieves all active domain block rules.
     * Cached in Caffeine ("rulesCache") to minimize Oracle ATP query load.
     */
    @Cacheable(value = CaffeineCacheConfig.CACHE_RULES, key = "'allRules'")
    public List<BlockedEntryEntity> getAllRules() {
        return repository.findAll();
    }

    /**
     * Adds a new domain blocking rule.
     * Evicts the Caffeine cache and pushes the domain to the Redis resolver set.
     */
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RULES, allEntries = true)
    public BlockedEntryEntity addRule(String domain, String category) {
        String cleanDomain = domain.trim().toLowerCase();
        if (cleanDomain.endsWith(".")) {
            cleanDomain = cleanDomain.substring(0, cleanDomain.length() - 1);
        }

        BlockedEntryEntity entity = new BlockedEntryEntity(cleanDomain, category);
        BlockedEntryEntity saved = repository.save(entity);

        // Sync to Redis set for App 1 instant blocking
        syncToRedisSet(cleanDomain, true);
        return saved;
    }

    /**
     * Removes a domain blocking rule.
     * Evicts the Caffeine cache and removes the domain from the Redis resolver set.
     */
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RULES, allEntries = true)
    public boolean deleteRule(String domain) {
        String cleanDomain = domain.trim().toLowerCase();
        if (repository.existsByDomain(cleanDomain)) {
            repository.deleteByDomain(cleanDomain);
            syncToRedisSet(cleanDomain, false);
            return true;
        }
        return false;
    }

    private void syncToRedisSet(String domain, boolean isAdd) {
        if (jedisPool == null) return;
        try (Jedis jedis = jedisPool.getResource()) {
            if (isAdd) {
                jedis.sadd("blocked_domains", domain);
                logger.info("Added domain '{}' to Redis set 'blocked_domains'", domain);
            } else {
                jedis.srem("blocked_domains", domain);
                logger.info("Removed domain '{}' from Redis set 'blocked_domains'", domain);
            }
        } catch (Exception e) {
            logger.error("Failed to sync domain '{}' to Redis set: {}", domain, e.getMessage());
        }
    }
}
