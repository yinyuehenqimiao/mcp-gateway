package com.mcp.gateway.controller;

import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.ToolMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gateway")
public class GatewayDebugController {

    private final DynamicToolRegistry dynamicToolRegistry;

    public GatewayDebugController(DynamicToolRegistry dynamicToolRegistry) {
        this.dynamicToolRegistry = dynamicToolRegistry;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        int total = dynamicToolRegistry.listAllBySlug().values().stream().mapToInt(List::size).sum();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("publishedServers", dynamicToolRegistry.listAllBySlug().size());
        body.put("publishedTools", total);
        body.put("defaultSseTools", dynamicToolRegistry.listMappings().size());
        return body;
    }

    /**
     * 查询已发布工具。
     * - 不传 slug：按 MCP Server 分组返回全部
     * - 传 slug：只返回该分组
     */
    @GetMapping("/tools")
    public Object tools(@RequestParam(required = false) String slug) {
        if (slug != null && !slug.isBlank()) {
            return dynamicToolRegistry.listBySlug(slug).stream()
                    .map(m -> toView(slug, m))
                    .toList();
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        dynamicToolRegistry.listAllBySlug().forEach((serverSlug, mappings) -> {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("slug", serverSlug);
            group.put("sseUrl", "http://localhost:18090/mcp/" + serverSlug + "/sse");
            group.put("messageEndpoint", "http://localhost:18090/mcp/" + serverSlug + "/message");
            group.put("toolCount", mappings.size());
            group.put("tools", mappings.stream().map(m -> toView(serverSlug, m)).toList());
            groups.add(group);
        });
        return groups;
    }

    private Map<String, Object> toView(String slug, ToolMapping mapping) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("slug", slug);
        view.put("toolName", mapping.getToolName());
        view.put("description", mapping.getDescription());
        view.put("httpMethod", mapping.getHttpMethod());
        view.put("pathTemplate", mapping.getPathTemplate());
        view.put("baseUrl", mapping.getBaseUrl());
        view.put("inputSchema", mapping.getInputSchemaJson());
        return view;
    }
}
