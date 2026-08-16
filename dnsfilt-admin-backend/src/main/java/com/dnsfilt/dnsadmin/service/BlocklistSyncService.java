package com.dnsfilt.dnsadmin.service;

import com.dnsfilt.dnsadmin.entity.DomainRule;
import com.dnsfilt.dnsadmin.repository.DomainRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class BlocklistSyncService {
    private static final Logger logger = LoggerFactory.getLogger(BlocklistSyncService.class);

    private final DomainRuleRepository domainRuleRepo;
    private final String rawRedisUrl;
    private JedisPool jedisPool;

    public BlocklistSyncService(DomainRuleRepository domainRuleRepo,
                                @Value("${redis.url:redis://localhost:6379}") String redisUrl) {
        this.domainRuleRepo = domainRuleRepo;
        this.rawRedisUrl = redisUrl;
        initJedisPool(redisUrl);
    }

    private synchronized void initJedisPool(String rawUrl) {
        if (this.jedisPool != null && !this.jedisPool.isClosed()) {
            return;
        }
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(50);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(2);
            poolConfig.setTestWhileIdle(true);

            ParsedRedisUri parsed = parseRedisUrl(rawUrl);
            if (parsed != null && parsed.host != null && !parsed.host.isEmpty()) {
                if (parsed.password != null && !parsed.password.isEmpty()) {
                    this.jedisPool = new JedisPool(poolConfig, parsed.host, parsed.port, 3000, parsed.user, parsed.password, parsed.ssl);
                } else {
                    this.jedisPool = new JedisPool(poolConfig, parsed.host, parsed.port, 3000, null, null, parsed.ssl);
                }
                logger.info("BlocklistSyncService JedisPool configured for → {}:{} (user={}, ssl={})",
                        parsed.host, parsed.port, parsed.user, parsed.ssl);
            } else {
                logger.warn("Could not parse valid Redis host from URL '{}'", rawUrl);
            }
        } catch (Exception e) {
            logger.warn("JedisPool initialization deferred: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void onStartup() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1500);
                fullSync();
            } catch (Exception ignored) {}
        });
    }

    public boolean syncRuleToRedis(DomainRule rule) {
        if (jedisPool == null) {
            initJedisPool(rawRedisUrl);
            if (jedisPool == null) return false;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            String cleanDomain = rule.getDomain().trim().toLowerCase();
            String redisKey = "blocklist:rule:" + cleanDomain;
            String jsonVal = String.format(
                "{\"action\":\"%s\",\"matchType\":\"%s\",\"category\":\"%s\",\"severity\":\"%s\",\"status\":\"%s\"}",
                rule.getAction(), rule.getMatchType(), rule.getCategory(), rule.getSeverity(), rule.getStatus()
            );

            if ("ACTIVE".equalsIgnoreCase(rule.getStatus())) {
                jedis.set(redisKey, jsonVal);
                if ("BLOCK".equalsIgnoreCase(rule.getAction())) {
                    jedis.sadd("blocked_domains", cleanDomain);
                } else {
                    jedis.srem("blocked_domains", cleanDomain);
                }
            } else {
                jedis.del(redisKey);
                jedis.srem("blocked_domains", cleanDomain);
            }

            // Increment atomic blocklist version
            long newVersion = jedis.incr("blocklist:version");
            logger.info("Synced rule for domain '{}' ({}) to Redis (Blocklist version: {})", cleanDomain, rule.getAction(), newVersion);
            return true;
        } catch (JedisConnectionException e) {
            logger.warn("Redis unavailable for domain rule sync ({}). Rule is preserved in Oracle DB.", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Failed to sync domain rule {} to Redis: {}", rule.getDomain(), e.getMessage());
            return false;
        }
    }

    public boolean deleteRuleFromRedis(String domain) {
        if (jedisPool == null) {
            initJedisPool(rawRedisUrl);
            if (jedisPool == null) return false;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            String cleanDomain = domain.trim().toLowerCase();
            jedis.del("blocklist:rule:" + cleanDomain);
            jedis.srem("blocked_domains", cleanDomain);
            long newVersion = jedis.incr("blocklist:version");
            logger.info("Deleted rule for domain '{}' from Redis (Blocklist version: {})", cleanDomain, newVersion);
            return true;
        } catch (JedisConnectionException e) {
            logger.warn("Redis unavailable for domain delete ({}). Rule deleted from Oracle DB.", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Failed to delete domain rule {} from Redis: {}", domain, e.getMessage());
            return false;
        }
    }

    public int fullSync() {
        if (jedisPool == null) {
            initJedisPool(rawRedisUrl);
            if (jedisPool == null) return 0;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            List<DomainRule> activeRules = domainRuleRepo.findByStatus("ACTIVE");
            for (DomainRule rule : activeRules) {
                String cleanDomain = rule.getDomain().trim().toLowerCase();
                String redisKey = "blocklist:rule:" + cleanDomain;
                String jsonVal = String.format(
                    "{\"action\":\"%s\",\"matchType\":\"%s\",\"category\":\"%s\",\"severity\":\"%s\",\"status\":\"%s\"}",
                    rule.getAction(), rule.getMatchType(), rule.getCategory(), rule.getSeverity(), rule.getStatus()
                );
                jedis.set(redisKey, jsonVal);
                if ("BLOCK".equalsIgnoreCase(rule.getAction())) {
                    jedis.sadd("blocked_domains", cleanDomain);
                } else {
                    jedis.srem("blocked_domains", cleanDomain);
                }
            }
            long version = jedis.incr("blocklist:version");
            logger.info("Completed full blocklist sync of {} rules to Redis (Blocklist Version: {})", activeRules.size(), version);
            return activeRules.size();
        } catch (JedisConnectionException e) {
            logger.warn("Redis connection unavailable during fullSync: {}. Rules are safely stored in Oracle DB.", e.getMessage());
            return 0;
        } catch (Exception e) {
            logger.error("Failed full blocklist sync to Redis: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Robust Redis URL parser handling base64/special characters in passwords (such as '//').
     */
    private static ParsedRedisUri parseRedisUrl(String rawUrl) {
        try {
            boolean ssl = rawUrl.startsWith("rediss://");
            String withoutScheme = rawUrl.substring(rawUrl.indexOf("://") + 3);

            String host = null;
            int port = 6379;
            String user = "default";
            String password = null;

            // 1. Look for the LAST '@' separating auth credentials from host:port
            int atIndex = withoutScheme.lastIndexOf('@');
            String hostPart;
            if (atIndex >= 0) {
                String authPart = withoutScheme.substring(0, atIndex);
                hostPart = withoutScheme.substring(atIndex + 1);

                // Parse user:password
                int colonIndex = authPart.indexOf(':');
                if (colonIndex >= 0) {
                    user = URLDecoder.decode(authPart.substring(0, colonIndex), StandardCharsets.UTF_8);
                    password = URLDecoder.decode(authPart.substring(colonIndex + 1), StandardCharsets.UTF_8);
                } else {
                    password = URLDecoder.decode(authPart, StandardCharsets.UTF_8);
                }
            } else {
                hostPart = withoutScheme;
            }

            // 2. Remove trailing database index (e.g. host:6379/0) from hostPart ONLY
            int slashIndex = hostPart.indexOf('/');
            if (slashIndex >= 0) {
                hostPart = hostPart.substring(0, slashIndex);
            }

            // 3. Parse host:port
            int hostColonIndex = hostPart.lastIndexOf(':');
            if (hostColonIndex >= 0) {
                host = hostPart.substring(0, hostColonIndex);
                port = Integer.parseInt(hostPart.substring(hostColonIndex + 1));
            } else {
                host = hostPart;
            }

            return new ParsedRedisUri(host, port, user, password, ssl);
        } catch (Exception e) {
            logger.error("Failed parsing Redis URL '{}': {}", rawUrl, e.getMessage());
            return null;
        }
    }

    private static class ParsedRedisUri {
        final String host;
        final int port;
        final String user;
        final String password;
        final boolean ssl;

        ParsedRedisUri(String host, int port, String user, String password, boolean ssl) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
            this.ssl = ssl;
        }
    }
}
