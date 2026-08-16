package com.dnsfilt.dnsadmin.dto.rule;

/**
 * Domain block rule request payload.
 */
public record CreateRuleRequest(String domain, String category) {}
