package com.mcp.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record ParameterDto(
            @NotBlank String name,
            @NotBlank String in,
            String description,
            boolean required,
            String type
    ) {
    }

    public record CreateApiRequest(
            @NotBlank String name,
            String toolName,
            String description,
            @NotBlank String httpMethod,
            @NotBlank String pathTemplate,
            List<ParameterDto> parameters,
            String requestBodySchema,
            String headersJson,
            Boolean enabled
    ) {
    }

    public record UpdateApiRequest(
            String name,
            String toolName,
            String description,
            String httpMethod,
            String pathTemplate,
            List<ParameterDto> parameters,
            String requestBodySchema,
            String headersJson,
            Boolean enabled
    ) {
    }

    public record ApiResponse(
            Long id,
            Long systemId,
            String name,
            String toolName,
            String description,
            String httpMethod,
            String pathTemplate,
            String parametersJson,
            String requestBodySchema,
            String headersJson,
            Boolean enabled,
            String sourceType,
            String operationId,
            String inputSchema
    ) {
    }

    public record ImportOpenApiRequest(
            String openapiUrl,
            String openapiContent,
            Boolean replaceExisting
    ) {
    }

    public record ImportResult(
            int imported,
            int updated,
            List<String> toolNames
    ) {
    }

    public record TestCallRequest(
            @NotNull Long apiId,
            String argumentsJson
    ) {
    }
}
