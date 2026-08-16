package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.ResolverConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResolverConfigRepository extends JpaRepository<ResolverConfig, Long> {
}
