package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.ToolCallAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolCallAuditRepository extends JpaRepository<ToolCallAudit, Long> {

    @Query("""
            SELECT a FROM ToolCallAudit a
            WHERE (:slug IS NULL OR :slug = '' OR a.slug = :slug)
              AND (:toolName IS NULL OR :toolName = '' OR a.toolName = :toolName)
              AND (:success IS NULL OR a.success = :success)
            """)
    Page<ToolCallAudit> search(
            @Param("slug") String slug,
            @Param("toolName") String toolName,
            @Param("success") Boolean success,
            Pageable pageable);
}
