package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.McpServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {
    Optional<McpServerEntity> findBySlug(String slug);
    List<McpServerEntity> findByPublishedTrue();
    boolean existsBySlug(String slug);
}
