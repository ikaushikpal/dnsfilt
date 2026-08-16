package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.DomainRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainRuleRepository extends JpaRepository<DomainRule, Long> {
    Optional<DomainRule> findByDomain(String domain);
    List<DomainRule> findByStatus(String status);
    void deleteByDomain(String domain);
}
