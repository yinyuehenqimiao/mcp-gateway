package com.mcp.gateway.service.tool;

import com.mcp.gateway.service.openapi.ParsedOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时 MCP Tool 与下游 HTTP 的映射。
 */
public class ToolMapping {

    private final Long apiId;
    private final Long systemId;
    private final String toolName;
    private final String description;
    private final String httpMethod;
    private final String pathTemplate;
    private final String baseUrl;
    private final String authType;
    private final String authConfig;
    private final List<ParsedOperation.Param> parameters;
    private final String inputSchemaJson;
    private final String headersJson;

    public ToolMapping(
            Long apiId,
            Long systemId,
            String toolName,
            String description,
            String httpMethod,
            String pathTemplate,
            String baseUrl,
            String authType,
            String authConfig,
            List<ParsedOperation.Param> parameters,
            String inputSchemaJson,
            String headersJson) {
        this.apiId = apiId;
        this.systemId = systemId;
        this.toolName = toolName;
        this.description = description;
        this.httpMethod = httpMethod;
        this.pathTemplate = pathTemplate;
        this.baseUrl = baseUrl;
        this.authType = authType;
        this.authConfig = authConfig;
        this.parameters = List.copyOf(parameters);
        this.inputSchemaJson = inputSchemaJson;
        this.headersJson = headersJson;
    }

    public Long getApiId() {
        return apiId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDescription() {
        return description;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public List<ParsedOperation.Param> getParameters() {
        return parameters;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public List<ParsedOperation.Param> pathParameters() {
        List<ParsedOperation.Param> result = new ArrayList<>();
        for (ParsedOperation.Param parameter : parameters) {
            if ("path".equalsIgnoreCase(parameter.in())) {
                result.add(parameter);
            }
        }
        return result;
    }

    public List<ParsedOperation.Param> queryParameters() {
        List<ParsedOperation.Param> result = new ArrayList<>();
        for (ParsedOperation.Param parameter : parameters) {
            if ("query".equalsIgnoreCase(parameter.in())) {
                result.add(parameter);
            }
        }
        return result;
    }

    public List<ParsedOperation.Param> headerParameters() {
        List<ParsedOperation.Param> result = new ArrayList<>();
        for (ParsedOperation.Param parameter : parameters) {
            if ("header".equalsIgnoreCase(parameter.in())) {
                result.add(parameter);
            }
        }
        return result;
    }
}
