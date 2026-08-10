package com.mcp.gateway.service;

import com.mcp.gateway.common.exception.BusinessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.domain.entity.ApiEndpoint;
import com.mcp.gateway.domain.entity.BizSystem;
import com.mcp.gateway.domain.repository.ApiEndpointRepository;
import com.mcp.gateway.domain.repository.McpServerApiRepository;
import com.mcp.gateway.service.openapi.OpenApiDocumentParser;
import com.mcp.gateway.service.openapi.ParsedOperation;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.HttpToolForwarder;
import com.mcp.gateway.service.tool.InputSchemaBuilder;
import com.mcp.gateway.service.tool.ToolMapping;
import com.mcp.gateway.dto.ApiDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final McpServerApiRepository mcpServerApiRepository;
    private final BizSystemService bizSystemService;
    private final OpenApiDocumentParser openApiDocumentParser;
    private final InputSchemaBuilder inputSchemaBuilder;
    private final HttpToolForwarder httpToolForwarder;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public ApiEndpointService(
            ApiEndpointRepository apiEndpointRepository,
            McpServerApiRepository mcpServerApiRepository,
            BizSystemService bizSystemService,
            OpenApiDocumentParser openApiDocumentParser,
            InputSchemaBuilder inputSchemaBuilder,
            HttpToolForwarder httpToolForwarder,
            DynamicToolRegistry dynamicToolRegistry,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.mcpServerApiRepository = mcpServerApiRepository;
        this.bizSystemService = bizSystemService;
        this.openApiDocumentParser = openApiDocumentParser;
        this.inputSchemaBuilder = inputSchemaBuilder;
        this.httpToolForwarder = httpToolForwarder;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ApiResponse> listBySystem(Long systemId) {
        bizSystemService.require(systemId);
        return apiEndpointRepository.findBySystemIdOrderByIdAsc(systemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.ApiResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ApiDtos.ApiResponse createManual(Long systemId, ApiDtos.CreateApiRequest request) {
        bizSystemService.require(systemId);
        String toolName = request.toolName() == null || request.toolName().isBlank()
                ? OpenApiDocumentParser.sanitize(request.name())
                : OpenApiDocumentParser.sanitize(request.toolName());
        if (apiEndpointRepository.findBySystemIdAndToolName(systemId, toolName).isPresent()) {
            throw new BusinessException("toolName 已存在: " + toolName);
        }

        ApiEndpoint api = new ApiEndpoint();
        api.setSystemId(systemId);
        api.setName(request.name());
        api.setToolName(toolName);
        api.setDescription(request.description());
        api.setHttpMethod(request.httpMethod().toUpperCase(Locale.ROOT));
        api.setPathTemplate(request.pathTemplate());
        api.setParametersJson(writeParams(request.parameters()));
        api.setRequestBodySchema(request.requestBodySchema());
        api.setHeadersJson(request.headersJson());
        api.setEnabled(request.enabled() == null || request.enabled());
        api.setSourceType("MANUAL");
        ApiEndpoint saved = apiEndpointRepository.save(api);
        return toResponse(saved);
    }

    @Transactional
    public ApiDtos.ApiResponse update(Long id, ApiDtos.UpdateApiRequest request) {
        ApiEndpoint api = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            api.setName(request.name());
        }
        if (request.toolName() != null && !request.toolName().isBlank()) {
            String toolName = OpenApiDocumentParser.sanitize(request.toolName());
            Optional<ApiEndpoint> exists = apiEndpointRepository.findBySystemIdAndToolName(api.getSystemId(), toolName);
            if (exists.isPresent() && !exists.get().getId().equals(api.getId())) {
                throw new BusinessException("toolName 已存在: " + toolName);
            }
            api.setToolName(toolName);
        }
        if (request.description() != null) {
            api.setDescription(request.description());
        }
        if (request.httpMethod() != null && !request.httpMethod().isBlank()) {
            api.setHttpMethod(request.httpMethod().toUpperCase(Locale.ROOT));
        }
        if (request.pathTemplate() != null && !request.pathTemplate().isBlank()) {
            api.setPathTemplate(request.pathTemplate());
        }
        if (request.parameters() != null) {
            api.setParametersJson(writeParams(request.parameters()));
        }
        if (request.requestBodySchema() != null) {
            api.setRequestBodySchema(request.requestBodySchema());
        }
        if (request.headersJson() != null) {
            api.setHeadersJson(request.headersJson());
        }
        if (request.enabled() != null) {
            api.setEnabled(request.enabled());
        }
        ApiEndpoint saved = apiEndpointRepository.save(api);
        dynamicToolRegistry.refresh();
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ApiEndpoint api = require(id);
        mcpServerApiRepository.deleteByApiId(api.getId());
        apiEndpointRepository.delete(api);
        dynamicToolRegistry.refresh();
    }

    @Transactional
    public ApiDtos.ImportResult importOpenApi(Long systemId, ApiDtos.ImportOpenApiRequest request) {
        bizSystemService.require(systemId);
        String content = resolveOpenApiContent(request);
        List<ParsedOperation> operations = openApiDocumentParser.parse(content);

        if (Boolean.TRUE.equals(request.replaceExisting())) {
            List<ApiEndpoint> existing = apiEndpointRepository.findBySystemIdOrderByIdAsc(systemId);
            for (ApiEndpoint api : existing) {
                if ("OPENAPI".equals(api.getSourceType()) || "SWAGGER".equals(api.getSourceType())) {
                    // 先清绑定，避免被 MCP Server 引用时删除失败
                    mcpServerApiRepository.deleteByApiId(api.getId());
                    apiEndpointRepository.delete(api);
                }
            }
            apiEndpointRepository.flush();
        }

        int imported = 0;
        int updated = 0;
        List<String> toolNames = new ArrayList<>();
        for (ParsedOperation operation : operations) {
            Optional<ApiEndpoint> existing = apiEndpointRepository
                    .findBySystemIdAndToolName(systemId, operation.toolName());
            if (existing.isPresent()) {
                ApiEndpoint api = existing.get();
                fillFromOperation(api, operation);
                apiEndpointRepository.save(api);
                updated++;
            } else {
                ApiEndpoint api = new ApiEndpoint();
                api.setSystemId(systemId);
                fillFromOperation(api, operation);
                api.setEnabled(true);
                apiEndpointRepository.save(api);
                imported++;
            }
            toolNames.add(operation.toolName());
        }
        dynamicToolRegistry.refresh();
        return new ApiDtos.ImportResult(imported, updated, toolNames);
    }

    public String testCall(ApiDtos.TestCallRequest request) {
        ApiEndpoint api = require(request.apiId());
        BizSystem system = bizSystemService.require(api.getSystemId());
        ToolMapping mapping = dynamicToolRegistry.toMapping(api, system);
        return httpToolForwarder.forward(mapping, request.argumentsJson());
    }

    public ApiEndpoint require(Long id) {
        return apiEndpointRepository.findById(id)
                .orElseThrow(() -> new BusinessException("API 不存在: " + id));
    }

    private String resolveOpenApiContent(ApiDtos.ImportOpenApiRequest request) {
        if (request.openapiContent() != null && !request.openapiContent().isBlank()) {
            return sanitizeDocument(request.openapiContent());
        }
        if (request.openapiUrl() == null || request.openapiUrl().isBlank()) {
            throw new BusinessException("请提供 openapiUrl 或 openapiContent");
        }
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(request.openapiUrl().trim())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new BusinessException("OpenAPI URL 返回空内容: " + request.openapiUrl());
            }
            return sanitizeDocument(body);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    "拉取 OpenAPI 失败，请确认地址可访问且网关能连到该服务: "
                            + request.openapiUrl() + "，原因: " + ex.getMessage(),
                    ex);
        }
    }

    private static String sanitizeDocument(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1).trim();
        }
        if (content.isEmpty()) {
            throw new BusinessException("OpenAPI 内容为空");
        }
        if (!(content.startsWith("{") || content.startsWith("["))) {
            throw new BusinessException("目前仅支持 JSON 格式的 OpenAPI/Swagger（请粘贴 /v3/api-docs 的 JSON，不支持 YAML）");
        }
        return content;
    }

    private void fillFromOperation(ApiEndpoint api, ParsedOperation operation) {
        api.setName(operation.name());
        api.setToolName(operation.toolName());
        api.setDescription(operation.description());
        api.setHttpMethod(operation.httpMethod());
        api.setPathTemplate(operation.pathTemplate());
        api.setParametersJson(operation.parametersJson());
        api.setRequestBodySchema(operation.requestBodySchemaJson());
        api.setSourceType("OPENAPI");
        api.setOperationId(operation.operationId());
    }

    private String writeParams(List<ApiDtos.ParameterDto> parameters) {
        try {
            if (parameters == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            throw new BusinessException("parameters 序列化失败", ex);
        }
    }

    private ApiDtos.ApiResponse toResponse(ApiEndpoint api) {
        return new ApiDtos.ApiResponse(
                api.getId(),
                api.getSystemId(),
                api.getName(),
                api.getToolName(),
                api.getDescription(),
                api.getHttpMethod(),
                api.getPathTemplate(),
                api.getParametersJson(),
                api.getRequestBodySchema(),
                api.getHeadersJson(),
                api.getEnabled(),
                api.getSourceType(),
                api.getOperationId(),
                inputSchemaBuilder.build(api)
        );
    }
}
