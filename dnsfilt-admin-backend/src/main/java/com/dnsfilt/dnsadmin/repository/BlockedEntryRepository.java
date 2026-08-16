package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.BlockedEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlockedEntryRepository extends JpaRepository<BlockedEntryEntity, Long> {
    Optional<BlockedEntryEntity> findByDomain(String domain);
    boolean existsByDomain(String domain);
    void deleteByDomain(String domain);
}
