package com.dnsfilt.dnsadmin.controller;

import com.dnsfilt.dnsadmin.dto.rule.CreateRuleRequest;
import com.dnsfilt.dnsadmin.entity.BlockedEntryEntity;
import com.dnsfilt.dnsadmin.service.RuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/legacy-rules")
@CrossOrigin(origins = "*")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ResponseEntity<List<BlockedEntryEntity>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @PostMapping
    public ResponseEntity<BlockedEntryEntity> addRule(@RequestBody CreateRuleRequest request) {
        String domain = request.domain();
        String category = request.category() != null && !request.category().trim().isEmpty() 
                ? request.category() 
                : "MALWARE";
        if (domain == null || domain.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ruleService.addRule(domain, category));
    }

    @DeleteMapping("/{domain}")
    public ResponseEntity<Void> deleteRule(@PathVariable String domain) {
        boolean removed = ruleService.deleteRule(domain);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
