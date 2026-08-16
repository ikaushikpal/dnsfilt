package com.dnsfilt.dnsadmin.dto.analytics;

/**
 * Time-series traffic point response.
 */
public record TrafficPointResponse(
    String time,
    long totalQueries,
    long blockedQueries
) {}
