package com.dnsfilt.dnsadmin.repository;

import com.dnsfilt.dnsadmin.entity.BlockCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlockCategoryRepository extends JpaRepository<BlockCategory, Long> {
    Optional<BlockCategory> findByName(String name);
}
