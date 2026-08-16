package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "block_categories")
public class BlockCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    public BlockCategory() {}

    public BlockCategory(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = "ACTIVE";
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(String status) { this.status = status; }
}
