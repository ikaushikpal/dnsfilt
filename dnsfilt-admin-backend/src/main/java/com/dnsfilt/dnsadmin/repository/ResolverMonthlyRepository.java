package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ResolverMonthlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResolverMonthlyRepository extends JpaRepository<ResolverMonthlyStats, Long> {
    Optional<ResolverMonthlyStats> findByYearMonth(String yearMonth);
    List<ResolverMonthlyStats> findAllByOrderByYearMonthAsc();
}
