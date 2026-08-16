package com.dnsfilt.dnsanalytics.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_hourly_stats", indexes = {
    @Index(name = "idx_client_hour", columnList = "clientHash, hourTimestamp")
})
public class ClientHourlyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime hourTimestamp;

    @Column(nullable = false, length = 32)
    private String clientHash;

    private long totalQueries;
    private long allowedQueries;
    private long blockedQueries;
    private long nxdomainQueries;
    private long servfailQueries;
    private long cacheHits;
    private long cacheMisses;

    public ClientHourlyStats() {}

    public ClientHourlyStats(LocalDateTime hourTimestamp, String clientHash, long totalQueries, long allowedQueries,
                             long blockedQueries, long nxdomainQueries, long servfailQueries, long cacheHits, long cacheMisses) {
        this.hourTimestamp = hourTimestamp;
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
    public LocalDateTime getHourTimestamp() { return hourTimestamp; }
    public String getClientHash() { return clientHash; }
    public long getTotalQueries() { return totalQueries; }
    public long getAllowedQueries() { return allowedQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public long getNxdomainQueries() { return nxdomainQueries; }
    public long getServfailQueries() { return servfailQueries; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }

    public void setHourTimestamp(LocalDateTime hourTimestamp) { this.hourTimestamp = hourTimestamp; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setAllowedQueries(long allowedQueries) { this.allowedQueries = allowedQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setNxdomainQueries(long nxdomainQueries) { this.nxdomainQueries = nxdomainQueries; }
    public void setServfailQueries(long servfailQueries) { this.servfailQueries = servfailQueries; }
    public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
    public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
}
