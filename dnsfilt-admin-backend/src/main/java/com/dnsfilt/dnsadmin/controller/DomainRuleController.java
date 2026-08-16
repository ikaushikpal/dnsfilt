package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.config.CaffeineCacheConfig;
import com.dnsfilt.dnsadmin.dto.common.MessageResponse;
import com.dnsfilt.dnsadmin.dto.rule.CreateDomainRuleRequest;
import com.dnsfilt.dnsadmin.entity.BlockCategory;
import com.dnsfilt.dnsadmin.entity.DomainRule;
import com.dnsfilt.dnsadmin.repository.BlockCategoryRepository;
import com.dnsfilt.dnsadmin.repository.DomainRuleRepository;
import com.dnsfilt.dnsadmin.service.BlocklistSyncService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DomainRuleController
 * 
 * Provides RESTful management for active DNS security filtering rules (blocklist, allowlist, severity, category)
 * and security categorization metadata consumed by the Angular Admin UI (dnsfilt-ui).
 * 
 * Caching & Sync Architecture:
 * - GET requests are cached in-memory via Caffeine ("rulesCache").
 * - POST and DELETE mutations immediately purge the Caffeine cache via @CacheEvict and sync updates to Redis.
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = "*")
public class DomainRuleController {

    private final DomainRuleRepository domainRuleRepo;
    private final BlockCategoryRepository categoryRepo;
    private final BlocklistSyncService syncService;

    public DomainRuleController(DomainRuleRepository domainRuleRepo,
                                BlockCategoryRepository categoryRepo,
                                BlocklistSyncService syncService) {
        this.domainRuleRepo = domainRuleRepo;
        this.categoryRepo = categoryRepo;
        this.syncService = syncService;
    }

    /**
     * Lists all available security block categories (e.g. MALWARE, PHISHING, ADS, SOCIAL).
     */
    @GetMapping("/categories")
    @Cacheable(value = CaffeineCacheConfig.CACHE_RULES, key = "'categories'")
    public ResponseEntity<List<BlockCategory>> getCategories() {
        return ResponseEntity.ok(categoryRepo.findAll());
    }

    /**
     * Lists all active domain filtering rules.
     */
    @GetMapping
    @Cacheable(value = CaffeineCacheConfig.CACHE_RULES, key = "'allDomainRules'")
    public ResponseEntity<List<DomainRule>> getAllRules() {
        return ResponseEntity.ok(domainRuleRepo.findAll());
    }

    /**
     * Creates or updates a domain security filtering rule.
     * Evicts the Caffeine cache and pushes changes to Redis for DNS resolver enforcement.
     */
    @PostMapping
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RULES, allEntries = true)
    public ResponseEntity<DomainRule> createOrUpdateRule(@RequestBody CreateDomainRuleRequest request) {
        String rawDomain = request.domain();
        if (rawDomain == null || rawDomain.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        final String domain = rawDomain.trim().toLowerCase();

        String action = request.action() != null ? request.action() : "BLOCK";
        String matchType = request.matchType() != null ? request.matchType() : "DOMAIN_AND_SUBDOMAINS";
        String category = request.category() != null ? request.category() : "OTHER";
        String severity = request.severity() != null ? request.severity() : "MEDIUM";
        String reason = request.reason() != null ? request.reason() : "Admin rule entry";

        DomainRule rule = domainRuleRepo.findByDomain(domain)
                .orElseGet(() -> new DomainRule(domain, action, matchType, category, severity, reason));

        rule.setAction(action);
        rule.setMatchType(matchType);
        rule.setCategory(category);
        rule.setSeverity(severity);
        rule.setReason(reason);
        rule.setStatus("ACTIVE");

        DomainRule saved = domainRuleRepo.save(rule);
        syncService.syncRuleToRedis(saved);

        return ResponseEntity.ok(saved);
    }

    /**
     * Triggers a full synchronization of all active domain rules from Oracle ATP database into Redis.
     * Increments the blocklist version, forcing immediate in-memory cache invalidation across all resolver instances.
     */
    @PostMapping("/sync")
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RULES, allEntries = true)
    public ResponseEntity<MessageResponse> syncAllRulesToRedis() {
        int count = syncService.fullSync();
        return ResponseEntity.ok(new MessageResponse("Successfully synchronized " + count + " active rules to Redis. Blocklist version incremented."));
    }

    /**
     * Deletes an active domain rule by domain name.
     * Evicts the Caffeine cache and removes the rule from Redis.
     */
    @DeleteMapping("/{domain}")
    @Transactional
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RULES, allEntries = true)
    public ResponseEntity<MessageResponse> deleteRule(@PathVariable String domain) {
        String cleanDomain = domain.trim().toLowerCase();
        domainRuleRepo.deleteByDomain(cleanDomain);
        syncService.deleteRuleFromRedis(cleanDomain);
        return ResponseEntity.ok(new MessageResponse("Rule for domain " + cleanDomain + " removed successfully."));
    }
}
