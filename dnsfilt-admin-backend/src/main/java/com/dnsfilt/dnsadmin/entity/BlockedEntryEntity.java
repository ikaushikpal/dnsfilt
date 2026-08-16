package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "blocked_entries")
public class BlockedEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String category;

    public BlockedEntryEntity() {}

    public BlockedEntryEntity(String domain, String category) {
        this.domain = domain;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
