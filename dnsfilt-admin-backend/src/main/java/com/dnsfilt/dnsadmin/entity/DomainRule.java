package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "domain_rules", indexes = {
    @Index(name = "idx_domain", columnList = "domain")
})
public class DomainRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String domain;

    @Column(nullable = false, length = 16)
    private String action = "BLOCK"; // BLOCK or ALLOW

    @Column(nullable = false, length = 32)
    private String matchType = "DOMAIN_AND_SUBDOMAINS"; // EXACT or DOMAIN_AND_SUBDOMAINS

    @Column(length = 64)
    private String category = "OTHER";

    @Column(length = 16)
    private String severity = "MEDIUM"; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE"; // ACTIVE or INACTIVE

    @Column(length = 32)
    private String source = "ADMIN";

    @Column(length = 255)
    private String reason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public DomainRule() {}

    public DomainRule(String domain, String action, String matchType, String category, String severity, String reason) {
        this.domain = domain;
        this.action = action != null ? action : "BLOCK";
        this.matchType = matchType != null ? matchType : "DOMAIN_AND_SUBDOMAINS";
        this.category = category != null ? category : "OTHER";
        this.severity = severity != null ? severity : "MEDIUM";
        this.reason = reason;
        this.status = "ACTIVE";
        this.source = "ADMIN";
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public String getAction() { return action; }
    public String getMatchType() { return matchType; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public void setDomain(String domain) { this.domain = domain; }
    public void setAction(String action) { this.action = action; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public void setCategory(String category) { this.category = category; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setStatus(String status) { this.status = status; }
    public void setSource(String source) { this.source = source; }
    public void setReason(String reason) { this.reason = reason; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
