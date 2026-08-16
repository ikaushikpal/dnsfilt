package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ResolverMonthlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResolverMonthlyRepository extends JpaRepository<ResolverMonthlyStats, Long> {
    Optional<ResolverMonthlyStats> findByYearMonth(String yearMonth);
}
