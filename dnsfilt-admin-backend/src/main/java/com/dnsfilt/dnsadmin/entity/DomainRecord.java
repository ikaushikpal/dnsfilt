package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "domain_records")
public class DomainRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String recordType = "A"; // A, AAAA, CNAME, MX, TXT

    @Column(nullable = false)
    private String ipAddress = "127.0.0.1";

    @Column(nullable = false)
    private Integer ttl = 300;

    private String description;

    public DomainRecord() {}

    public DomainRecord(String domain, String recordType, String ipAddress, Integer ttl, String description) {
        this.domain = domain;
        this.recordType = recordType;
        this.ipAddress = ipAddress;
        this.ttl = ttl;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getTtl() { return ttl; }
    public void setTtl(Integer ttl) { this.ttl = ttl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
