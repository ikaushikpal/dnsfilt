package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.config.CaffeineCacheConfig;
import com.dnsfilt.dnsadmin.entity.DomainRecord;
import com.dnsfilt.dnsadmin.repository.DomainRecordRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DomainApiController
 * 
 * Manages custom DNS domain override records (A, AAAA, CNAME, TXT) in Oracle ATP Database.
 * 
 * Caching:
 * - GET requests are cached in Caffeine ("domainsCache").
 * - POST, PUT, DELETE requests automatically purge the cache via @CacheEvict.
 */
@RestController
@RequestMapping("/api/v1/domains")
@CrossOrigin(origins = "*")
public class DomainApiController {

    private final DomainRecordRepository repository;

    public DomainApiController(DomainRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Cacheable(value = CaffeineCacheConfig.CACHE_DOMAINS, key = "'allDomains'")
    public ResponseEntity<List<DomainRecord>> listDomains() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    @CacheEvict(value = CaffeineCacheConfig.CACHE_DOMAINS, allEntries = true)
    public ResponseEntity<DomainRecord> createDomain(@RequestBody DomainRecord domainRecord) {
        if (domainRecord.getDomain() == null || domainRecord.getDomain().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        DomainRecord saved = repository.save(domainRecord);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @CacheEvict(value = CaffeineCacheConfig.CACHE_DOMAINS, allEntries = true)
    public ResponseEntity<DomainRecord> updateDomain(@PathVariable Long id, @RequestBody DomainRecord updatedRecord) {
        return repository.findById(id).map(existing -> {
            if (updatedRecord.getDomain() != null) existing.setDomain(updatedRecord.getDomain());
            if (updatedRecord.getRecordType() != null) existing.setRecordType(updatedRecord.getRecordType());
            if (updatedRecord.getIpAddress() != null) existing.setIpAddress(updatedRecord.getIpAddress());
            if (updatedRecord.getTtl() != null) existing.setTtl(updatedRecord.getTtl());
            if (updatedRecord.getDescription() != null) existing.setDescription(updatedRecord.getDescription());
            return ResponseEntity.ok(repository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = CaffeineCacheConfig.CACHE_DOMAINS, allEntries = true)
    public ResponseEntity<Void> deleteDomain(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
