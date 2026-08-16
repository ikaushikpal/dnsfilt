package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.DomainRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomainRecordRepository extends JpaRepository<DomainRecord, Long> {
    Optional<DomainRecord> findByDomain(String domain);
}
