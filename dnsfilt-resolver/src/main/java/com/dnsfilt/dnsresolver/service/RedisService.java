package com.dnsfilt.dnsresolver.service;

import com.dnsfilt.dnsresolver.utility.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

/**
 * RedisService
 * 
 * Provides thread-safe, connection-pooled Redis data operations for L2 caching and blocklist rules.
 * Implements the Bill Pugh Singleton Pattern.
 */
public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);

    private RedisService() {}

    /**
     * Bill Pugh Singleton Holder
     */
    private static class InstanceHolder {
        private static final RedisService INSTANCE = new RedisService();
    }

    public static RedisService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public void set(String key, String value, int ttl) {
        try (Jedis jedis = RedisManager.getJedis()) {
            jedis.setex(key, ttl, value);
            logger.debug("Stored key '{}' in Redis (TTL {}s)", key, ttl);
        } catch (Exception e) {
            logger.error("Redis SET error for key '{}': {}", key, e.getMessage());
        }
    }

    public String get(String key) {
        try (Jedis jedis = RedisManager.getJedis()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Redis GET error for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    public void delete(String key) {
        try (Jedis jedis = RedisManager.getJedis()) {
            jedis.del(key);
            logger.debug("Deleted key '{}' from Redis", key);
        } catch (Exception e) {
            logger.error("Redis DELETE error for key '{}': {}", key, e.getMessage());
        }
    }

    // Push log event string to a Redis Queue (List) for App 2 (Spring Boot) to consume asynchronously
    public void lpush(String queueName, String jsonValue) {
        try (Jedis jedis = RedisManager.getJedis()) {
            jedis.lpush(queueName, jsonValue);
        } catch (Exception e) {
            logger.error("Redis LPUSH error for queue '{}': {}", queueName, e.getMessage());
        }
    }

    // Fast O(1) set membership check (for blocked domain set)
    public boolean isSetMember(String setName, String member) {
        try (Jedis jedis = RedisManager.getJedis()) {
            return jedis.sismember(setName, member);
        } catch (Exception e) {
            logger.error("Redis SISMEMBER error for set '{}': {}", setName, e.getMessage());
            return false;
        }
    }
}
