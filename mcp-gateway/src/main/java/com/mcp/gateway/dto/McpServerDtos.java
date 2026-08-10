package com.mcp.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class McpServerDtos {

    private McpServerDtos() {
    }

    public record CreateMcpServerRequest(
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            String accessToken,
            @NotEmpty List<Long> apiIds
    ) {
    }

    public record UpdateMcpServerRequest(
            String name,
            String description,
            String accessToken,
            List<Long> apiIds
    ) {
    }

    public record McpServerResponse(
            Long id,
            String name,
            String slug,
            String description,
            String accessToken,
            Boolean published,
            List<Long> apiIds,
            String sseUrl,
            String messageEndpoint
    ) {
    }

    public record ToolPreview(
            String toolName,
            String description,
            String httpMethod,
            String pathTemplate,
            String inputSchema
    ) {
    }
}
