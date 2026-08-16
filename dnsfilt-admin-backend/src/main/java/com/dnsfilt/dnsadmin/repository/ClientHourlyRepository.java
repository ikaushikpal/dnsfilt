package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ClientHourlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientHourlyRepository extends JpaRepository<ClientHourlyStats, Long> {
    Optional<ClientHourlyStats> findByClientHashAndHourTimestamp(String clientHash, LocalDateTime hourTimestamp);
    List<ClientHourlyStats> findByClientHash(String clientHash);

    @Query("SELECT COUNT(DISTINCT c.clientHash) FROM ClientHourlyStats c")
    long countDistinctClients();

    @Query("SELECT c.clientHash, SUM(c.totalQueries), SUM(c.blockedQueries) FROM ClientHourlyStats c GROUP BY c.clientHash ORDER BY SUM(c.totalQueries) DESC")
    List<Object[]> getClientSummaryAggregate();
}
