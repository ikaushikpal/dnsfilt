package com.dnsfilt.dnsadmin.dto.analytics;

/**
 * Security category traffic breakdown response.
 */
public record CategoryBreakdownResponse(
    String category,
    long totalQueries,
    long blockedQueries
) {}
