package com.mcp.gateway.controller;

import com.mcp.gateway.service.McpServerAdminService;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.ToolMapping;
import com.mcp.gateway.dto.McpServerDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerAdminService mcpServerAdminService;
    private final DynamicToolRegistry dynamicToolRegistry;

    public McpServerController(
            McpServerAdminService mcpServerAdminService,
            DynamicToolRegistry dynamicToolRegistry) {
        this.mcpServerAdminService = mcpServerAdminService;
        this.dynamicToolRegistry = dynamicToolRegistry;
    }

    @PostMapping
    public McpServerDtos.McpServerResponse create(@Valid @RequestBody McpServerDtos.CreateMcpServerRequest request) {
        return mcpServerAdminService.create(request);
    }

    @GetMapping
    public List<McpServerDtos.McpServerResponse> list() {
        return mcpServerAdminService.list();
    }

    @GetMapping("/{id}")
    public McpServerDtos.McpServerResponse get(@PathVariable Long id) {
        return mcpServerAdminService.get(id);
    }

    @PutMapping("/{id}")
    public McpServerDtos.McpServerResponse update(
            @PathVariable Long id,
            @RequestBody McpServerDtos.UpdateMcpServerRequest request) {
        return mcpServerAdminService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public McpServerDtos.McpServerResponse publish(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean published) {
        return mcpServerAdminService.publish(id, published);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mcpServerAdminService.delete(id);
    }

    @GetMapping("/{id}/tools")
    public List<McpServerDtos.ToolPreview> previewTools(@PathVariable Long id) {
        return mcpServerAdminService.previewTools(id);
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        dynamicToolRegistry.refresh();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("servers", dynamicToolRegistry.listAllBySlug().keySet());
        body.put("groups", dynamicToolRegistry.listAllBySlug().entrySet().stream().map(e -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("slug", e.getKey());
            g.put("count", e.getValue().size());
            g.put("tools", e.getValue().stream().map(ToolMapping::getToolName).toList());
            return g;
        }).toList());
        return body;
    }
}
