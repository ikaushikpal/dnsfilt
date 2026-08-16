package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ClientTopDomainsHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientTopDomainsHourlyRepository extends JpaRepository<ClientTopDomainsHourly, Long> {
    Optional<ClientTopDomainsHourly> findByClientHashAndHourTimestampAndDomain(String clientHash, LocalDateTime hourTimestamp, String domain);
    List<ClientTopDomainsHourly> findByClientHash(String clientHash);

    @Query("SELECT c.domain, SUM(c.totalQueries), SUM(c.blockedQueries), COUNT(DISTINCT c.clientHash) FROM ClientTopDomainsHourly c GROUP BY c.domain ORDER BY SUM(c.blockedQueries) DESC, SUM(c.totalQueries) DESC")
    List<Object[]> getTopDomainsAggregate();

    @Query("SELECT COUNT(DISTINCT c.domain) FROM ClientTopDomainsHourly c WHERE c.clientHash = :clientHash")
    long countDistinctDomainsByClientHash(@Param("clientHash") String clientHash);
}
