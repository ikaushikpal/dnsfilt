package com.dnsfilt.dnsanalytics.entity;

import jakarta.persistence.*;

/**
 * ClientMonthlyStats
 * 
 * Precomputed per-client monthly rollup table for security auditing and user behavior trends.
 */
@Entity
@Table(name = "client_monthly_stats", indexes = {
    @Index(name = "idx_client_month", columnList = "clientHash, yearMonth")
})
public class ClientMonthlyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String yearMonth; // Format: "YYYY-MM" (e.g. "2026-08")

    @Column(nullable = false, length = 32)
    private String clientHash;

    private long totalQueries;
    private long allowedQueries;
    private long blockedQueries;
    private long nxdomainQueries;
    private long servfailQueries;
    private long cacheHits;
    private long cacheMisses;

    public ClientMonthlyStats() {}

    public ClientMonthlyStats(String yearMonth, String clientHash, long totalQueries, long allowedQueries,
                              long blockedQueries, long nxdomainQueries, long servfailQueries, long cacheHits, long cacheMisses) {
        this.yearMonth = yearMonth;
        this.clientHash = clientHash;
        this.totalQueries = totalQueries;
        this.allowedQueries = allowedQueries;
        this.blockedQueries = blockedQueries;
        this.nxdomainQueries = nxdomainQueries;
        this.servfailQueries = servfailQueries;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public Long getId() { return id; }
    public String getYearMonth() { return yearMonth; }
    public String getClientHash() { return clientHash; }
    public long getTotalQueries() { return totalQueries; }
    public long getAllowedQueries() { return allowedQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public long getNxdomainQueries() { return nxdomainQueries; }
    public long getServfailQueries() { return servfailQueries; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }

    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setAllowedQueries(long allowedQueries) { this.allowedQueries = allowedQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setNxdomainQueries(long nxdomainQueries) { this.nxdomainQueries = nxdomainQueries; }
    public void setServfailQueries(long servfailQueries) { this.servfailQueries = servfailQueries; }
    public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
    public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
}
