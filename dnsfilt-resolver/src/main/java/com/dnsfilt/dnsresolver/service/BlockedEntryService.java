package com.dnsfilt.dnsresolver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockedEntryService
 * 
 * Provides blacklist domain validation by delegating to Redis set checks with hierarchy scanning.
 * Implements the Bill Pugh Singleton Pattern.
 */
public class BlockedEntryService {
    private static final Logger logger = LoggerFactory.getLogger(BlockedEntryService.class);

    private final RedisService redisService;

    private BlockedEntryService() {
        this.redisService = RedisService.getInstance();
    }

    private static class InstanceHolder {
        private static final BlockedEntryService INSTANCE = new BlockedEntryService();
    }

    public static BlockedEntryService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Checks if domain or any of its parent domains exist in the Redis 'blocked_domains' set.
     */
    public boolean isDomainBlackListed(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        String cleanDomain = domain.toLowerCase().trim();
        if (cleanDomain.endsWith(".")) {
            cleanDomain = cleanDomain.substring(0, cleanDomain.length() - 1);
        }

        String curr = cleanDomain;
        while (curr != null && !curr.isEmpty()) {
            if (redisService.isSetMember("blocked_domains", curr)) {
                return true;
            }
            int dot = curr.indexOf('.');
            if (dot > 0 && dot < curr.length() - 1) {
                curr = curr.substring(dot + 1);
            } else {
                break;
            }
        }

        return false;
    }
}
