package com.dnsfilt.dnsresolver.utility;

import com.dnsfilt.dnsresolver.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * RedisManager
 * 
 * Manages the Jedis connection pool for dnsfilt-resolver.
 * Implements robust URI parsing that safely handles base64 / special-character passwords (such as '//' or '@').
 */
public class RedisManager {
    private static volatile JedisPool jedisPool;
    private static final Logger logger = LoggerFactory.getLogger(RedisManager.class);

    public static synchronized void init() {
        if (jedisPool != null) return;

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(100);
            poolConfig.setMaxIdle(25);
            poolConfig.setMinIdle(10);
            poolConfig.setTestOnBorrow(false);
            poolConfig.setTestOnReturn(false);
            poolConfig.setTestWhileIdle(true);

            AppConfig config = AppConfig.getInstance();
            String redisUrlStr = config.getEnvVariable("redis.url");
            String host = config.getEnvVariable("redis.host");
            String portStr = config.getEnvVariable("redis.port");
            String user = config.getEnvVariable("redis.user");
            String password = config.getEnvVariable("redis.password");
            String sslStr = config.getEnvVariable("redis.ssl");

            logger.info("Redis config — url={}, host={}, port={}, ssl={}", redisUrlStr, host, portStr, sslStr);

            // 1. Try parsing from REDIS_URL first
            if (redisUrlStr != null && (redisUrlStr.startsWith("rediss://") || redisUrlStr.startsWith("redis://"))) {
                ParsedRedisUri parsed = parseRedisUrl(redisUrlStr);
                if (parsed != null && parsed.host != null && !parsed.host.isEmpty()) {
                    if (parsed.password != null && !parsed.password.isEmpty()) {
                        jedisPool = new JedisPool(poolConfig, parsed.host, parsed.port, 5000, parsed.user, parsed.password, parsed.ssl);
                    } else {
                        jedisPool = new JedisPool(poolConfig, parsed.host, parsed.port, 5000, null, null, parsed.ssl);
                    }
                    logger.info("JedisPool initialized via redis.url → {}:{} (user={}, ssl={})", parsed.host, parsed.port, parsed.user, parsed.ssl);
                } else {
                    logger.warn("Could not parse valid host from redis.url: {}", redisUrlStr);
                }
            }

            // 2. Fallback to discrete host/port/user/password fields
            if (jedisPool == null && host != null && !host.trim().isEmpty() && !"none".equalsIgnoreCase(host)) {
                int port = (portStr != null) ? Integer.parseInt(portStr) : 6379;
                boolean ssl = "true".equalsIgnoreCase(sslStr);
                int timeout = 5000;

                if (password != null && !password.trim().isEmpty()) {
                    String username = (user != null && !user.trim().isEmpty()) ? user : "default";
                    jedisPool = new JedisPool(poolConfig, host, port, timeout, username, password, ssl);
                } else {
                    jedisPool = new JedisPool(poolConfig, host, port, timeout, null, null, ssl);
                }
                logger.info("JedisPool initialized via redis.host → {}:{} (user={}, ssl={})", host, port, user, ssl);
            }

            // 3. Fallback warning
            if (jedisPool == null) {
                logger.warn("No valid Redis URL or host found in .env. Falling back to localhost:6379");
                jedisPool = new JedisPool(poolConfig, "localhost", 6379);
            }

        } catch (Exception e) {
            logger.error("Failed to initialize Redis connection pool: {}", e.getMessage(), e);
        }
    }

    /**
     * Robust Redis URL parser that handles complex passwords containing '//', '@', '+', '=', etc.
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

    public static Jedis getJedis() {
        if (jedisPool == null) {
            init();
        }
        return jedisPool.getResource();
    }

    public static void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis connection pool closed.");
        }
    }
}
