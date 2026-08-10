package com.mcp.gateway.service.openapi;

import java.util.List;

public record ParsedOperation(
        String name,
        String toolName,
        String description,
        String httpMethod,
        String pathTemplate,
        String operationId,
        List<Param> parameters,
        String requestBodySchemaJson,
        String parametersJson,
        String inputSchemaJson
) {
    public record Param(
            String name,
            String in,
            String description,
            boolean required,
            String type
    ) {
    }
}
