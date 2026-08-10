package com.mcp.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public final class SystemDtos {

    private SystemDtos() {
    }

    public record CreateSystemRequest(
            @NotBlank String name,
            @NotBlank String code,
            @NotBlank String baseUrl,
            String description,
            String authType,
            String authConfig
    ) {
    }

    public record UpdateSystemRequest(
            String name,
            String baseUrl,
            String description,
            String authType,
            String authConfig
    ) {
    }

    public record SystemResponse(
            Long id,
            String name,
            String code,
            String baseUrl,
            String description,
            String authType,
            String authConfig
    ) {
    }
}
