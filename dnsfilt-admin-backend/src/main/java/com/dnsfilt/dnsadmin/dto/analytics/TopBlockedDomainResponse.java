package com.dnsfilt.dnsadmin.dto.analytics;

/**
 * Top blocked domain response entry.
 */
public record TopBlockedDomainResponse(
    int rank,
    String domain,
    String category,
    long requests,
    long blockedRequests,
    long clients
) {}
