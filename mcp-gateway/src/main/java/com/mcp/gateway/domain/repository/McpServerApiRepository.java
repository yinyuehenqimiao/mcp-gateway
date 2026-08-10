package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.McpServerApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface McpServerApiRepository extends JpaRepository<McpServerApi, Long> {
    List<McpServerApi> findByServerId(Long serverId);

    @Modifying
    @Transactional
    void deleteByServerId(Long serverId);

    @Modifying
    @Transactional
    void deleteByApiId(Long apiId);
}
