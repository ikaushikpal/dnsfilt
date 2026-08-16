package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ClientDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ClientDailyRepository extends JpaRepository<ClientDailyStats, Long> {
    Optional<ClientDailyStats> findByClientHashAndDateTimestamp(String clientHash, LocalDate dateTimestamp);
}
