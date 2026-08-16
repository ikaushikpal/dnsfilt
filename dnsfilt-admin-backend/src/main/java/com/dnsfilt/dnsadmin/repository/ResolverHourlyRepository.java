package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ResolverHourlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResolverHourlyRepository extends JpaRepository<ResolverHourlyStats, Long> {
    List<ResolverHourlyStats> findByHourTimestampBetween(LocalDateTime start, LocalDateTime end);
    List<ResolverHourlyStats> findByHourTimestampBetweenOrderByHourTimestampAsc(LocalDateTime start, LocalDateTime end);
    List<ResolverHourlyStats> findByHourTimestampBefore(LocalDateTime cutoff);

    @Query("SELECT SUM(r.totalQueries) FROM ResolverHourlyStats r")
    Long sumTotalQueries();

    @Query("SELECT SUM(r.blockedQueries) FROM ResolverHourlyStats r")
    Long sumBlockedQueries();

    @Query("SELECT SUM(r.cacheHits) FROM ResolverHourlyStats r")
    Long sumCacheHits();

    @Query("SELECT AVG(r.avgLatencyMs) FROM ResolverHourlyStats r")
    Double avgLatencyMs();

    @Query("SELECT SUM(r.totalQueries) FROM ResolverHourlyStats r WHERE r.hourTimestamp BETWEEN :start AND :end")
    Long sumTotalQueriesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(r.blockedQueries) FROM ResolverHourlyStats r WHERE r.hourTimestamp BETWEEN :start AND :end")
    Long sumBlockedQueriesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(r.cacheHits) FROM ResolverHourlyStats r WHERE r.hourTimestamp BETWEEN :start AND :end")
    Long sumCacheHitsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT AVG(r.avgLatencyMs) FROM ResolverHourlyStats r WHERE r.hourTimestamp BETWEEN :start AND :end")
    Double avgLatencyMsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ResolverHourlyStats> findTop24ByOrderByHourTimestampDesc();
}
