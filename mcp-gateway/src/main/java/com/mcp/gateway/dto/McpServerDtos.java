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
            /** JWT（推荐）或 FIXED */
            String authMode,
            /** FIXED 模式必填；JWT 模式可空 */
            String accessToken,
            /** JWT 模式可选：下游签发密钥，空则用网关默认 gateway.jwt.secret */
            String jwtSecret,
            @NotEmpty List<Long> apiIds
    ) {
    }

    public record UpdateMcpServerRequest(
            String name,
            String description,
            String authMode,
            String accessToken,
            String jwtSecret,
            List<Long> apiIds
    ) {
    }

    public record McpServerResponse(
            Long id,
            String name,
            String slug,
            String description,
            String authMode,
            String accessToken,
            String jwtSecret,
            Boolean published,
            List<Long> apiIds,
            String sseUrl,
            String messageEndpoint,
            String streamableUrl
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
