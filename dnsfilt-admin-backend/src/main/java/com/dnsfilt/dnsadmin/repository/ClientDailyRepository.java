package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ClientDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientDailyRepository extends JpaRepository<ClientDailyStats, Long> {
    Optional<ClientDailyStats> findByClientHashAndDateTimestamp(String clientHash, LocalDate dateTimestamp);

    List<ClientDailyStats> findByDateTimestampBetween(LocalDate start, LocalDate end);

    @Query("SELECT COUNT(DISTINCT c.clientHash) FROM ClientDailyStats c WHERE c.dateTimestamp BETWEEN :start AND :end")
    Long countDistinctClientsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT c.clientHash, SUM(c.totalQueries), SUM(c.blockedQueries) FROM ClientDailyStats c WHERE c.dateTimestamp BETWEEN :start AND :end GROUP BY c.clientHash ORDER BY SUM(c.totalQueries) DESC")
    List<Object[]> getClientSummaryAggregateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
