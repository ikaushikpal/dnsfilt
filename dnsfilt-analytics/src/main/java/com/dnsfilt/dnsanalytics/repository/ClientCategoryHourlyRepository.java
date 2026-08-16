package com.dnsfilt.dnsanalytics.repository;

import com.dnsfilt.dnsanalytics.entity.ClientCategoryHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCategoryHourlyRepository extends JpaRepository<ClientCategoryHourly, Long> {
    Optional<ClientCategoryHourly> findByClientHashAndCategoryAndHourTimestamp(String clientHash, String category, LocalDateTime hourTimestamp);
    List<ClientCategoryHourly> findByHourTimestampBefore(LocalDateTime cutoff);
    void deleteByHourTimestampBefore(LocalDateTime cutoff);
}
