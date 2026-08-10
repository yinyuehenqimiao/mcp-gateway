package com.mcp.gateway.service;

import com.mcp.gateway.common.exception.BusinessException;
import com.mcp.gateway.config.GatewayProperties;
import com.mcp.gateway.domain.entity.ApiEndpoint;
import com.mcp.gateway.domain.entity.McpServerApi;
import com.mcp.gateway.domain.entity.McpServerEntity;
import com.mcp.gateway.domain.repository.ApiEndpointRepository;
import com.mcp.gateway.domain.repository.McpServerApiRepository;
import com.mcp.gateway.domain.repository.McpServerRepository;
import com.mcp.gateway.dto.McpServerDtos;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.InputSchemaBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class McpServerAdminService {

    private final McpServerRepository mcpServerRepository;
    private final McpServerApiRepository mcpServerApiRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final InputSchemaBuilder inputSchemaBuilder;
    private final GatewayProperties gatewayProperties;

    public McpServerAdminService(
            McpServerRepository mcpServerRepository,
            McpServerApiRepository mcpServerApiRepository,
            ApiEndpointRepository apiEndpointRepository,
            DynamicToolRegistry dynamicToolRegistry,
            InputSchemaBuilder inputSchemaBuilder,
            GatewayProperties gatewayProperties) {
        this.mcpServerRepository = mcpServerRepository;
        this.mcpServerApiRepository = mcpServerApiRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.inputSchemaBuilder = inputSchemaBuilder;
        this.gatewayProperties = gatewayProperties;
    }

    @Transactional
    public McpServerDtos.McpServerResponse create(McpServerDtos.CreateMcpServerRequest request) {
        if (mcpServerRepository.existsBySlug(request.slug())) {
            throw new BusinessException("slug 已存在: " + request.slug());
        }
        validateApiIds(request.apiIds());

        String authMode = normalizeAuthMode(request.authMode());
        String accessToken = blankToNull(request.accessToken());
        String jwtSecret = blankToNull(request.jwtSecret());
        validateAuthConfig(authMode, accessToken);

        McpServerEntity server = new McpServerEntity();
        server.setName(request.name());
        server.setSlug(request.slug());
        server.setDescription(request.description());
        server.setAuthMode(authMode);
        server.setAccessToken(accessToken);
        server.setJwtSecret(jwtSecret);
        server.setPublished(false);
        McpServerEntity saved = mcpServerRepository.save(server);
        replaceBindings(saved.getId(), request.apiIds());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<McpServerDtos.McpServerResponse> list() {
        return mcpServerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public McpServerDtos.McpServerResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public McpServerDtos.McpServerResponse update(Long id, McpServerDtos.UpdateMcpServerRequest request) {
        McpServerEntity server = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            server.setName(request.name());
        }
        if (request.description() != null) {
            server.setDescription(request.description());
        }
        if (request.authMode() != null && !request.authMode().isBlank()) {
            server.setAuthMode(normalizeAuthMode(request.authMode()));
        }
        // accessToken / jwtSecret：传 null 表示不改；传 "" 表示清空
        if (request.accessToken() != null) {
            server.setAccessToken(blankToNull(request.accessToken()));
        }
        if (request.jwtSecret() != null) {
            server.setJwtSecret(blankToNull(request.jwtSecret()));
        }
        validateAuthConfig(server.getAuthMode(), server.getAccessToken());

        if (request.apiIds() != null) {
            validateApiIds(request.apiIds());
            replaceBindings(server.getId(), request.apiIds());
        }
        McpServerEntity saved = mcpServerRepository.saveAndFlush(server);
        if (Boolean.TRUE.equals(saved.getPublished())) {
            dynamicToolRegistry.refresh();
        }
        return toResponse(saved);
    }

    @Transactional
    public McpServerDtos.McpServerResponse publish(Long id, boolean published) {
        McpServerEntity server = require(id);
        server.setPublished(published);
        McpServerEntity saved = mcpServerRepository.save(server);
        dynamicToolRegistry.refresh();
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        mcpServerRepository.delete(require(id));
        dynamicToolRegistry.refresh();
    }

    @Transactional(readOnly = true)
    public List<McpServerDtos.ToolPreview> previewTools(Long id) {
        McpServerEntity server = require(id);
        List<Long> apiIds = mcpServerApiRepository.findByServerId(server.getId()).stream()
                .map(McpServerApi::getApiId)
                .toList();
        if (apiIds.isEmpty()) {
            return List.of();
        }
        return apiEndpointRepository.findByIdInAndEnabledTrue(apiIds).stream()
                .map(api -> new McpServerDtos.ToolPreview(
                        api.getToolName(),
                        api.getDescription() == null ? api.getName() : api.getDescription(),
                        api.getHttpMethod(),
                        api.getPathTemplate(),
                        inputSchemaBuilder.build(api)
                ))
                .toList();
    }

    private void replaceBindings(Long serverId, List<Long> apiIds) {
        mcpServerApiRepository.deleteByServerId(serverId);
        mcpServerApiRepository.flush();
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(apiIds);
        for (Long apiId : uniqueIds) {
            McpServerApi bind = new McpServerApi();
            bind.setServerId(serverId);
            bind.setApiId(apiId);
            mcpServerApiRepository.save(bind);
        }
        mcpServerApiRepository.flush();
    }

    private void validateApiIds(List<Long> apiIds) {
        for (Long apiId : apiIds) {
            if (!apiEndpointRepository.existsById(apiId)) {
                throw new BusinessException("API 不存在: " + apiId);
            }
        }
    }

    private void validateAuthConfig(String authMode, String accessToken) {
        if ("FIXED".equals(normalizeAuthMode(authMode))
                && (accessToken == null || accessToken.isBlank())) {
            throw new BusinessException("固定令牌模式必须填写 Access Token（可粘贴业务登录 JWT）");
        }
    }

    private static String normalizeAuthMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "JWT";
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        return "FIXED".equals(m) ? "FIXED" : "JWT";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private McpServerEntity require(Long id) {
        return mcpServerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("MCP Server 不存在: " + id));
    }

    private McpServerDtos.McpServerResponse toResponse(McpServerEntity server) {
        List<Long> apiIds = mcpServerApiRepository.findByServerId(server.getId()).stream()
                .map(McpServerApi::getApiId)
                .toList();
        String base = trimSlash(gatewayProperties.getPublicBaseUrl());
        String sseUrl = base + "/mcp/" + server.getSlug() + "/sse";
        String messageEndpoint = base + "/mcp/" + server.getSlug() + "/message";
        return new McpServerDtos.McpServerResponse(
                server.getId(),
                server.getName(),
                server.getSlug(),
                server.getDescription(),
                server.getAuthMode() == null ? "JWT" : server.getAuthMode(),
                server.getAccessToken(),
                server.getJwtSecret(),
                server.getPublished(),
                apiIds,
                sseUrl,
                messageEndpoint
        );
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:18190";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
