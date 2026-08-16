package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ClientMonthlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientMonthlyRepository extends JpaRepository<ClientMonthlyStats, Long> {
    Optional<ClientMonthlyStats> findByClientHashAndYearMonth(String clientHash, String yearMonth);
    List<ClientMonthlyStats> findByYearMonth(String yearMonth);
}
