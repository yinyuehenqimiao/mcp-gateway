package com.mcp.gateway.service.tool;

import com.mcp.gateway.config.GatewayProperties;
import com.mcp.gateway.domain.entity.ApiEndpoint;
import com.mcp.gateway.domain.entity.BizSystem;
import com.mcp.gateway.domain.entity.McpServerApi;
import com.mcp.gateway.domain.entity.McpServerEntity;
import com.mcp.gateway.domain.repository.ApiEndpointRepository;
import com.mcp.gateway.domain.repository.BizSystemRepository;
import com.mcp.gateway.domain.repository.McpServerApiRepository;
import com.mcp.gateway.domain.repository.McpServerRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按 MCP Server slug 分组加载工具。
 * <p>
 * - 多租户入口：/mcp/{slug}/sse （每个发布单元独立工具集）
 * - 兼容入口：/sse （Spring AI，仅加载 gateway.default-server-slug）
 */
@Component
public class DynamicToolRegistry implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolRegistry.class);

    private final GatewayProperties properties;
    private final McpServerRepository mcpServerRepository;
    private final McpServerApiRepository mcpServerApiRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final BizSystemRepository bizSystemRepository;
    private final InputSchemaBuilder inputSchemaBuilder;
    private final HttpToolForwarder httpToolForwarder;

    /** slug -> tools */
    private volatile Map<String, List<ToolMapping>> toolsBySlug = Map.of();
    /** 兼容 Spring AI 全局 /sse */
    private volatile ToolCallback[] toolCallbacks = new ToolCallback[0];
    private volatile Map<String, ToolMapping> defaultToolMappings = Map.of();

    public DynamicToolRegistry(
            GatewayProperties properties,
            McpServerRepository mcpServerRepository,
            McpServerApiRepository mcpServerApiRepository,
            ApiEndpointRepository apiEndpointRepository,
            BizSystemRepository bizSystemRepository,
            InputSchemaBuilder inputSchemaBuilder,
            HttpToolForwarder httpToolForwarder) {
        this.properties = properties;
        this.mcpServerRepository = mcpServerRepository;
        this.mcpServerApiRepository = mcpServerApiRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.bizSystemRepository = bizSystemRepository;
        this.inputSchemaBuilder = inputSchemaBuilder;
        this.httpToolForwarder = httpToolForwarder;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized void refresh() {
        List<McpServerEntity> servers = mcpServerRepository.findByPublishedTrue();
        Map<String, List<ToolMapping>> bySlug = new LinkedHashMap<>();

        for (McpServerEntity server : servers) {
            List<ToolMapping> mappings = loadServerMappings(server);
            bySlug.put(server.getSlug(), List.copyOf(mappings));
            log.info("MCP slug={} 已加载 {} 个工具", server.getSlug(), mappings.size());
        }
        this.toolsBySlug = Map.copyOf(bySlug);

        String defaultSlug = properties.getDefaultServerSlug();
        List<ToolMapping> defaultMappings = List.of();
        if (defaultSlug != null && !defaultSlug.isBlank() && bySlug.containsKey(defaultSlug)) {
            defaultMappings = bySlug.get(defaultSlug);
        }

        Map<String, ToolMapping> defaultMap = new LinkedHashMap<>();
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolMapping mapping : defaultMappings) {
            if (defaultMap.containsKey(mapping.getToolName())) {
                continue;
            }
            defaultMap.put(mapping.getToolName(), mapping);
            callbacks.add(new GatewayToolCallback(mapping, httpToolForwarder));
        }
        this.defaultToolMappings = Map.copyOf(defaultMap);
        this.toolCallbacks = callbacks.toArray(ToolCallback[]::new);
        log.info("兼容入口 /sse 使用 slug={}，工具数={}", defaultSlug, toolCallbacks.length);
    }

    public Map<String, List<ToolMapping>> listAllBySlug() {
        return toolsBySlug;
    }

    public List<ToolMapping> listBySlug(String slug) {
        return toolsBySlug.getOrDefault(slug, List.of());
    }

    public Optional<McpServerEntity> findPublishedServer(String slug) {
        return mcpServerRepository.findBySlug(slug)
                .filter(s -> Boolean.TRUE.equals(s.getPublished()));
    }

    /** 兼容旧接口：返回 default slug 工具 */
    public List<ToolMapping> listMappings() {
        return new ArrayList<>(defaultToolMappings.values());
    }

    public Optional<ToolMapping> findMapping(String toolName) {
        return Optional.ofNullable(defaultToolMappings.get(toolName));
    }

    public Optional<ToolMapping> findMapping(String slug, String toolName) {
        return listBySlug(slug).stream()
                .filter(m -> m.getToolName().equals(toolName))
                .findFirst();
    }

    private List<ToolMapping> loadServerMappings(McpServerEntity server) {
        List<McpServerApi> binds = mcpServerApiRepository.findByServerId(server.getId());
        if (binds.isEmpty()) {
            return List.of();
        }
        List<Long> apiIds = binds.stream().map(McpServerApi::getApiId).toList();
        List<ApiEndpoint> apis = apiEndpointRepository.findByIdInAndEnabledTrue(apiIds);
        Map<Long, BizSystem> systemCache = new LinkedHashMap<>();
        List<ToolMapping> mappings = new ArrayList<>();
        for (ApiEndpoint api : apis) {
            BizSystem system = systemCache.computeIfAbsent(api.getSystemId(), id ->
                    bizSystemRepository.findById(id)
                            .orElseThrow(() -> new IllegalStateException("业务系统不存在: " + id)));
            mappings.add(toMapping(api, system));
        }
        return mappings;
    }

    public ToolMapping toMapping(ApiEndpoint api, BizSystem system) {
        return new ToolMapping(
                api.getId(),
                system.getId(),
                api.getToolName(),
                api.getDescription() == null || api.getDescription().isBlank()
                        ? api.getName()
                        : api.getDescription(),
                api.getHttpMethod(),
                api.getPathTemplate(),
                system.getBaseUrl(),
                system.getAuthType(),
                system.getAuthConfig(),
                inputSchemaBuilder.readParams(api.getParametersJson()),
                inputSchemaBuilder.build(api),
                api.getHeadersJson()
        );
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks;
    }

    private static final class GatewayToolCallback implements ToolCallback {
        private final ToolMapping mapping;
        private final HttpToolForwarder forwarder;
        private final ToolDefinition toolDefinition;

        private GatewayToolCallback(ToolMapping mapping, HttpToolForwarder forwarder) {
            this.mapping = mapping;
            this.forwarder = forwarder;
            this.toolDefinition = ToolDefinition.builder()
                    .name(mapping.getToolName())
                    .description(mapping.getDescription())
                    .inputSchema(mapping.getInputSchemaJson())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return forwarder.forward(mapping, toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }
}
