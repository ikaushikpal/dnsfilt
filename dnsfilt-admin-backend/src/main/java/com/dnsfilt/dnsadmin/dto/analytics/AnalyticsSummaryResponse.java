package com.dnsfilt.dnsadmin.dto.analytics;

/**
 * Global analytics summary metric response.
 */
public record AnalyticsSummaryResponse(
    long totalQueries,
    long blockedQueries,
    double blockRatePercent,
    double cacheHitPercent,
    double avgLatencyMs,
    long activeClients
) {}
