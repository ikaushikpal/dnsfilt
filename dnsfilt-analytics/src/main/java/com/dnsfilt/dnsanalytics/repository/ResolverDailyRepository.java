package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ResolverDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ResolverDailyRepository extends JpaRepository<ResolverDailyStats, Long> {
    Optional<ResolverDailyStats> findByDateTimestamp(LocalDate dateTimestamp);
}
