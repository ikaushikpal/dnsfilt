package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_top_domains_hourly", indexes = {
    @Index(name = "idx_top_dom_client_hour", columnList = "clientHash, hourTimestamp")
})
public class ClientTopDomainsHourly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime hourTimestamp;

    @Column(nullable = false, length = 32)
    private String clientHash;

    @Column(nullable = false, length = 255)
    private String domain;

    private long totalQueries;
    private long blockedQueries;
    private int domainRank;

    public ClientTopDomainsHourly() {}

    public ClientTopDomainsHourly(LocalDateTime hourTimestamp, String clientHash, String domain, long totalQueries, long blockedQueries, int domainRank) {
        this.hourTimestamp = hourTimestamp;
        this.clientHash = clientHash;
        this.domain = domain;
        this.totalQueries = totalQueries;
        this.blockedQueries = blockedQueries;
        this.domainRank = domainRank;
    }

    public Long getId() { return id; }
    public LocalDateTime getHourTimestamp() { return hourTimestamp; }
    public String getClientHash() { return clientHash; }
    public String getDomain() { return domain; }
    public long getTotalQueries() { return totalQueries; }
    public long getBlockedQueries() { return blockedQueries; }
    public int getDomainRank() { return domainRank; }

    public void setHourTimestamp(LocalDateTime hourTimestamp) { this.hourTimestamp = hourTimestamp; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
    public void setDomainRank(int domainRank) { this.domainRank = domainRank; }
}
