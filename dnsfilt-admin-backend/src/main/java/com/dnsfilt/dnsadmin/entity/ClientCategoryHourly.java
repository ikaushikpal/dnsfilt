package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_category_hourly", indexes = {
    @Index(name = "idx_cat_client_hour", columnList = "clientHash, hourTimestamp")
})
public class ClientCategoryHourly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime hourTimestamp;

    @Column(nullable = false, length = 32)
    private String clientHash;

    @Column(nullable = false, length = 64)
    private String category;

    private long totalQueries;
    private long blockedQueries;

    public ClientCategoryHourly() {}

    public ClientCategoryHourly(LocalDateTime hourTimestamp, String clientHash, String category, long totalQueries, long blockedQueries) {
        this.hourTimestamp = hourTimestamp;
        this.clientHash = clientHash;
        this.category = category;
        this.totalQueries = totalQueries;
        this.blockedQueries = blockedQueries;
    }

    public Long getId() { return id; }
    public LocalDateTime getHourTimestamp() { return hourTimestamp; }
    public String getClientHash() { return clientHash; }
    public String getCategory() { return category; }
    public long getTotalQueries() { return totalQueries; }
    public long getBlockedQueries() { return blockedQueries; }

    public void setHourTimestamp(LocalDateTime hourTimestamp) { this.hourTimestamp = hourTimestamp; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }
    public void setCategory(String category) { this.category = category; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
    public void setBlockedQueries(long blockedQueries) { this.blockedQueries = blockedQueries; }
}
