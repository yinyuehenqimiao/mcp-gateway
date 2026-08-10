package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.BizSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BizSystemRepository extends JpaRepository<BizSystem, Long> {
    Optional<BizSystem> findByCode(String code);
    boolean existsByCode(String code);
}
