package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ResolverHourlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResolverHourlyRepository extends JpaRepository<ResolverHourlyStats, Long> {
    Optional<ResolverHourlyStats> findByHourTimestamp(LocalDateTime hourTimestamp);
    List<ResolverHourlyStats> findByHourTimestampBefore(LocalDateTime cutoff);
}
