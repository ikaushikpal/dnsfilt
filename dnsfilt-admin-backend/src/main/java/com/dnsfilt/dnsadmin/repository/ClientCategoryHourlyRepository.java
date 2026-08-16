package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ClientCategoryHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCategoryHourlyRepository extends JpaRepository<ClientCategoryHourly, Long> {
    Optional<ClientCategoryHourly> findByClientHashAndHourTimestampAndCategory(String clientHash, LocalDateTime hourTimestamp, String category);
    List<ClientCategoryHourly> findByClientHash(String clientHash);

    @Query("SELECT c.category, SUM(c.totalQueries), SUM(c.blockedQueries) FROM ClientCategoryHourly c GROUP BY c.category ORDER BY SUM(c.totalQueries) DESC")
    List<Object[]> getCategorySummaryAggregate();
}
