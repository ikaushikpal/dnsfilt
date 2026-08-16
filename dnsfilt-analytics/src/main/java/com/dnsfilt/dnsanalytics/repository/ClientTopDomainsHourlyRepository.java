package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ClientTopDomainsHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientTopDomainsHourlyRepository extends JpaRepository<ClientTopDomainsHourly, Long> {
    Optional<ClientTopDomainsHourly> findByClientHashAndDomainAndHourTimestamp(String clientHash, String domain, LocalDateTime hourTimestamp);
    List<ClientTopDomainsHourly> findByHourTimestampBefore(LocalDateTime cutoff);
    void deleteByHourTimestampBefore(LocalDateTime cutoff);
}
