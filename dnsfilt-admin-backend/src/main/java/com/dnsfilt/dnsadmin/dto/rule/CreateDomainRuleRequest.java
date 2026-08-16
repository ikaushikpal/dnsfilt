package com.dnsfilt.dnsadmin.dto.rule;

/**
 * Domain rule creation/update request payload.
 */
public record CreateDomainRuleRequest(
    String domain,
    String action,
    String matchType,
    String category,
    String severity,
    String reason
) {}
