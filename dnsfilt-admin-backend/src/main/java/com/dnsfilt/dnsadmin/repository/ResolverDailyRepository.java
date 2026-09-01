package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ResolverDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResolverDailyRepository extends JpaRepository<ResolverDailyStats, Long> {
    Optional<ResolverDailyStats> findByDateTimestamp(LocalDate dateTimestamp);

    List<ResolverDailyStats> findByDateTimestampBetweenOrderByDateTimestampAsc(LocalDate start, LocalDate end);

    @Query("SELECT SUM(r.totalQueries) FROM ResolverDailyStats r WHERE r.dateTimestamp BETWEEN :start AND :end")
    Long sumTotalQueriesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(r.blockedQueries) FROM ResolverDailyStats r WHERE r.dateTimestamp BETWEEN :start AND :end")
    Long sumBlockedQueriesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(r.cacheHits) FROM ResolverDailyStats r WHERE r.dateTimestamp BETWEEN :start AND :end")
    Long sumCacheHitsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT AVG(r.avgLatencyMs) FROM ResolverDailyStats r WHERE r.dateTimestamp BETWEEN :start AND :end")
    Double avgLatencyMsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(r.totalQueries) FROM ResolverDailyStats r")
    Long sumTotalQueries();

    @Query("SELECT SUM(r.blockedQueries) FROM ResolverDailyStats r")
    Long sumBlockedQueries();

    @Query("SELECT SUM(r.cacheHits) FROM ResolverDailyStats r")
    Long sumCacheHits();

    @Query("SELECT AVG(r.avgLatencyMs) FROM ResolverDailyStats r")
    Double avgLatencyMs();
}
