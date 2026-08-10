package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.McpServerApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface McpServerApiRepository extends JpaRepository<McpServerApi, Long> {
    List<McpServerApi> findByServerId(Long serverId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from McpServerApi m where m.serverId = :serverId")
    void deleteByServerId(@Param("serverId") Long serverId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from McpServerApi m where m.apiId = :apiId")
    void deleteByApiId(@Param("apiId") Long apiId);
}
