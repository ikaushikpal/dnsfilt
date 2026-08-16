package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ClientHourlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientHourlyRepository extends JpaRepository<ClientHourlyStats, Long> {
    Optional<ClientHourlyStats> findByClientHashAndHourTimestamp(String clientHash, LocalDateTime hourTimestamp);
    List<ClientHourlyStats> findByHourTimestampBefore(LocalDateTime cutoff);
}
