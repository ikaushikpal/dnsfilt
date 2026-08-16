package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "resolver_configs")
public class ResolverConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer desiredCount = 3;

    @Column(nullable = false)
    private String desiredVersion = "1.0.0";

    public ResolverConfig() {}

    public ResolverConfig(Integer desiredCount, String desiredVersion) {
        this.desiredCount = desiredCount;
        this.desiredVersion = desiredVersion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getDesiredCount() { return desiredCount; }
    public void setDesiredCount(Integer desiredCount) { this.desiredCount = desiredCount; }

    public String getDesiredVersion() { return desiredVersion; }
    public void setDesiredVersion(String desiredVersion) { this.desiredVersion = desiredVersion; }
}
