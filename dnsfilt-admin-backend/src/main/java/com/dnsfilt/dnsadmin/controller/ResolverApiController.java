package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.config.CaffeineCacheConfig;
import com.dnsfilt.dnsadmin.dto.resolver.UpdateResolverCountRequest;
import com.dnsfilt.dnsadmin.dto.resolver.UpdateResolverVersionRequest;
import com.dnsfilt.dnsadmin.entity.ResolverConfig;
import com.dnsfilt.dnsadmin.repository.ResolverConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * ResolverApiController
 * 
 * Manages runtime scaling & versioning parameters for the dnsfilt-resolver
 * microservice cluster.
 * 
 * Caching & Push Notifications:
 * - GET /api/v1/resolver/config is cached in Caffeine ("resolverConfigCache").
 * - PUT updates evict cache and immediately trigger an asynchronous push
 * webhook to the Python orchestrator.
 */
@RestController
@RequestMapping("/api/v1/resolver")
@CrossOrigin(origins = "*")
public class ResolverApiController {
    private static final Logger logger = LoggerFactory.getLogger(ResolverApiController.class);

    private final ResolverConfigRepository repository;
    private final String orchestratorUrl;
    private final HttpClient httpClient;

    public ResolverApiController(ResolverConfigRepository repository,
            @Value("${orchestrator.url}") String orchestratorUrl) {
        this.repository = repository;
        this.orchestratorUrl = orchestratorUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    private ResolverConfig getOrCreateConfig() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            ResolverConfig c = new ResolverConfig(3, "1.0.0");
            return repository.save(c);
        });
    }

    @GetMapping("/config")
    @Cacheable(value = CaffeineCacheConfig.CACHE_RESOLVER_CONFIG, key = "'config'")
    public ResponseEntity<ResolverConfig> getConfig() {
        return ResponseEntity.ok(getOrCreateConfig());
    }

    @PutMapping("/count")
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RESOLVER_CONFIG, allEntries = true)
    public ResponseEntity<ResolverConfig> updateCount(@RequestBody UpdateResolverCountRequest request) {
        Integer count = request.count();
        if (count == null || count < 1) {
            return ResponseEntity.badRequest().build();
        }
        ResolverConfig config = getOrCreateConfig();
        config.setDesiredCount(count);
        ResolverConfig saved = repository.save(config);

        // Push immediate trigger to orchestrator
        triggerOrchestratorAsync();

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/version")
    @CacheEvict(value = CaffeineCacheConfig.CACHE_RESOLVER_CONFIG, allEntries = true)
    public ResponseEntity<ResolverConfig> updateVersion(@RequestBody UpdateResolverVersionRequest request) {
        String version = request.version();
        if (version == null || version.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ResolverConfig config = getOrCreateConfig();
        config.setDesiredVersion(version.trim());
        ResolverConfig saved = repository.save(config);

        // Push immediate trigger to orchestrator
        triggerOrchestratorAsync();

        return ResponseEntity.ok(saved);
    }

    /**
     * Asynchronously triggers the Python orchestrator to reconcile immediately
     * without waiting for the 60s periodic loop.
     */
    private void triggerOrchestratorAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(orchestratorUrl))
                        .timeout(Duration.ofSeconds(3))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                logger.info("Directly triggered orchestrator reconciliation at '{}'", orchestratorUrl);
            } catch (Exception e) {
                logger.debug("Orchestrator push trigger notification notice: {}", e.getMessage());
            }
        });
    }
}
