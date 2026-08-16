package com.dnsfilt.dnsadmin.dto.analytics;

/**
 * Top active client response entry with risk classification.
 */
public record TopClientResponse(
    String clientHash,
    long totalQueries,
    long blockedQueries,
    double blockRate,
    long distinctDomains,
    String riskLevel,
    String riskBadge
) {}
