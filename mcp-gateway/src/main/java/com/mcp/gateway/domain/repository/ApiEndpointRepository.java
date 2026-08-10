package com.mcp.gateway.domain.repository;

import com.mcp.gateway.domain.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    List<ApiEndpoint> findBySystemIdOrderByIdAsc(Long systemId);
    Optional<ApiEndpoint> findBySystemIdAndToolName(Long systemId, String toolName);
    List<ApiEndpoint> findByIdInAndEnabledTrue(Collection<Long> ids);
    List<ApiEndpoint> findByEnabledTrue();
}
