package com.dnsfilt.dnsanalytics.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "client_daily_stats", indexes = {
    @Index(name = "idx_client_date", columnList = "clientHash, dateTimestamp")
})
public class ClientDailyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dateTimestamp;

    @Column(nullable = false, length = 32)
    private String clientHash;

    private long totalQueries;
    private long allowedQueries;
    private long blockedQueries;
    private long nxdomainQueries;
    private long servfailQueries;
    private long cacheHits;
    private long cacheMisses;

    public ClientDailyStats() {}

    public ClientDailyStats(LocalDate dateTimestamp, String clientHash, long totalQueries, long allowedQueries,
                            long blockedQueries, long nxdomainQueries, long servfailQueries, long cacheHits, long cacheMisses) {
        this.dateTimestamp = dateTimestamp;
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
    public LocalDate getDateTimestamp() { return dateTimestamp; }
    public String getClientHash() { return clientHash; }
    public long getTotalQueries() { return totalQueries; }
    public long getAllowedQueries() { return allowedQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public long getNxdomainQueries() { return nxdomainQueries; }
    public long getServfailQueries() { return servfailQueries; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }

    public void setDateTimestamp(LocalDate dateTimestamp) { this.dateTimestamp = dateTimestamp; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setAllowedQueries(long allowedQueries) { this.allowedQueries = allowedQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setNxdomainQueries(long nxdomainQueries) { this.nxdomainQueries = nxdomainQueries; }
    public void setServfailQueries(long servfailQueries) { this.servfailQueries = servfailQueries; }
    public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
    public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
}
