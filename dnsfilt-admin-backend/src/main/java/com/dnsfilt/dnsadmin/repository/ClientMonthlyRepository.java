package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ClientMonthlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientMonthlyRepository extends JpaRepository<ClientMonthlyStats, Long> {
    Optional<ClientMonthlyStats> findByClientHashAndYearMonth(String clientHash, String yearMonth);
    List<ClientMonthlyStats> findByYearMonth(String yearMonth);

    @Query("SELECT COUNT(DISTINCT c.clientHash) FROM ClientMonthlyStats c WHERE c.yearMonth = :yearMonth")
    Long countDistinctClientsByYearMonth(@Param("yearMonth") String yearMonth);

    @Query("SELECT c.clientHash, SUM(c.totalQueries), SUM(c.blockedQueries) FROM ClientMonthlyStats c WHERE c.yearMonth = :yearMonth GROUP BY c.clientHash ORDER BY SUM(c.totalQueries) DESC")
    List<Object[]> getClientSummaryAggregateByYearMonth(@Param("yearMonth") String yearMonth);
}
