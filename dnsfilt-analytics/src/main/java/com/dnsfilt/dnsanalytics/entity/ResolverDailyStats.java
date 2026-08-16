package com.dnsfilt.dnsanalytics.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "resolver_daily_stats")
public class ResolverDailyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate dateTimestamp;

    private long totalQueries;
    private long allowedQueries;
    private long blockedQueries;
    private long nxdomainQueries;
    private long servfailQueries;
    private long cacheHits;
    private long cacheMisses;
    private double avgLatencyMs;

    public ResolverDailyStats() {}

    public ResolverDailyStats(LocalDate dateTimestamp, long totalQueries, long allowedQueries, long blockedQueries,
                              long nxdomainQueries, long servfailQueries, long cacheHits, long cacheMisses, double avgLatencyMs) {
        this.dateTimestamp = dateTimestamp;
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
    public LocalDate getDateTimestamp() { return dateTimestamp; }
    public long getTotalQueries() { return totalQueries; }
    public long getAllowedQueries() { return allowedQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public long getNxdomainQueries() { return nxdomainQueries; }
    public long getServfailQueries() { return servfailQueries; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public double getAvgLatencyMs() { return avgLatencyMs; }

    public void setDateTimestamp(LocalDate dateTimestamp) { this.dateTimestamp = dateTimestamp; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setAllowedQueries(long allowedQueries) { this.allowedQueries = allowedQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setNxdomainQueries(long nxdomainQueries) { this.nxdomainQueries = nxdomainQueries; }
    public void setServfailQueries(long servfailQueries) { this.servfailQueries = servfailQueries; }
    public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
    public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
}
