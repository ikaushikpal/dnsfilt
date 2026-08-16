package com.dnsfilt.dnsanalytics.entity;

import jakarta.persistence.*;

/**
 * ResolverMonthlyStats
 * 
 * Precomputed monthly rollup table for enterprise multi-month trend analysis.
 * Storing precomputed monthly snapshots eliminates costly full-table scans across millions of rows.
 */
@Entity
@Table(name = "resolver_monthly_stats")
public class ResolverMonthlyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 7)
    private String yearMonth; // Format: "YYYY-MM" (e.g. "2026-08")

    private long totalQueries;
    private long allowedQueries;
    private long blockedQueries;
    private long nxdomainQueries;
    private long servfailQueries;
    private long cacheHits;
    private long cacheMisses;
    private double avgLatencyMs;

    public ResolverMonthlyStats() {}

    public ResolverMonthlyStats(String yearMonth, long totalQueries, long allowedQueries, long blockedQueries,
                                long nxdomainQueries, long servfailQueries, long cacheHits, long cacheMisses, double avgLatencyMs) {
        this.yearMonth = yearMonth;
        this.totalQueries = totalQueries;
        this.allowedQueries = allowedQueries;
        this.blockedQueries = blockedQueries;
        this.nxdomainQueries = nxdomainQueries;
        this.servfailQueries = servfailQueries;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
        this.avgLatencyMs = avgLatencyMs;
    }

    public Long getId() { return id; }
    public String getYearMonth() { return yearMonth; }
    public long getTotalQueries() { return totalQueries; }
    public long getAllowedQueries() { return allowedQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public long getNxdomainQueries() { return nxdomainQueries; }
    public long getServfailQueries() { return servfailQueries; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public double getAvgLatencyMs() { return avgLatencyMs; }

    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setAllowedQueries(long allowedQueries) { this.allowedQueries = allowedQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setNxdomainQueries(long nxdomainQueries) { this.nxdomainQueries = nxdomainQueries; }
    public void setServfailQueries(long servfailQueries) { this.servfailQueries = servfailQueries; }
    public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
    public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
}
