package com.mcp.gateway.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.gateway.common.exception.BusinessException;
import com.mcp.gateway.domain.entity.McpServerEntity;
import com.mcp.gateway.service.ToolCallAuditService;
import com.mcp.gateway.service.auth.McpApiKeyAuthenticator;
import com.mcp.gateway.service.ratelimit.RedisRateLimiter;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.HttpToolForwarder;
import com.mcp.gateway.service.tool.ToolMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * 无状态 Streamable HTTP 入口（按 slug）：
 * POST /mcp/{slug}
 * <p>
 * 对齐 MCP 2026-07-28 思路：无 Session / 无长连接；可选校验
 * MCP-Protocol-Version、Mcp-Method、Mcp-Name 与 JSON-RPC body 一致。
 */
@RestController
@RequestMapping("/mcp/{slug}")
public class McpStreamableController {

    private static final Logger log = LoggerFactory.getLogger(McpStreamableController.class);
    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final int HEADER_MISMATCH = -32020;
    private static final long DEFERRED_TIMEOUT_MS = 30_000L;

    private final DynamicToolRegistry toolRegistry;
    private final HttpToolForwarder httpToolForwarder;
    private final ObjectMapper objectMapper;
    private final McpApiKeyAuthenticator apiKeyAuthenticator;
    private final RedisRateLimiter rateLimiter;
    private final ToolCallAuditService auditService;

    public McpStreamableController(
            DynamicToolRegistry toolRegistry,
            HttpToolForwarder httpToolForwarder,
            ObjectMapper objectMapper,
            McpApiKeyAuthenticator apiKeyAuthenticator,
            RedisRateLimiter rateLimiter,
            ToolCallAuditService auditService) {
        this.toolRegistry = toolRegistry;
        this.httpToolForwarder = httpToolForwarder;
        this.objectMapper = objectMapper;
        this.apiKeyAuthenticator = apiKeyAuthenticator;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object handle(
            @PathVariable String slug,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(value = "Mcp-Method", required = false) String mcpMethod,
            @RequestHeader(value = "Mcp-Name", required = false) String mcpName,
            @RequestBody String body) throws Exception {
        McpServerEntity server = requirePublished(slug);
        JsonNode message = objectMapper.readTree(body);
        if (message.isArray()) {
            return ResponseEntity.badRequest().body(error(null, -32600, "Streamable 入口请逐条发送 JSON-RPC，不支持批量数组"));
        }

        JsonNode id = message.get("id");
        String method = text(message, "method");
        if (method == null || method.isBlank()) {
            return ResponseEntity.badRequest().body(error(id, -32600, "Invalid Request: missing method"));
        }

        if (protocolVersion == null || protocolVersion.isBlank()) {
            log.debug("Streamable request without MCP-Protocol-Version slug={} method={}", slug, method);
        }

        ResponseEntity<?> mismatch = validateHeaders(id, method, message, mcpMethod, mcpName);
        if (mismatch != null) {
            return mismatch;
        }

        boolean isNotification = id == null || id.isNull();
        if (isNotification) {
            return ResponseEntity.accepted().build();
        }

        if ("tools/call".equals(method)) {
            return handleToolsCallAsync(server, slug, authorization, message, id);
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        try {
            switch (method) {
                case "initialize", "server/discover" -> response.set("result", initializeResult(server));
                case "ping" -> response.set("result", objectMapper.createObjectNode());
                case "tools/list" -> {
                    McpApiKeyAuthenticator.AuthContext auth = authenticate(server, authorization);
                    rateLimiter.checkOrThrow(auth.keyHash(), slug, null);
                    response.set("result", toolsListResult(slug));
                }
                default -> {
                    return ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
                }
            }
        } catch (BusinessException ex) {
            return mapBusinessException(id, ex);
        } catch (Exception ex) {
            log.error("Streamable handle failed slug={} method={}", slug, method, ex);
            return ResponseEntity.ok(error(id, -32603, ex.getMessage()));
        }
        return ResponseEntity.ok(response);
    }

    private DeferredResult<ResponseEntity<?>> handleToolsCallAsync(
            McpServerEntity server,
            String slug,
            String authorization,
            JsonNode message,
            JsonNode id) {
        DeferredResult<ResponseEntity<?>> deferred = new DeferredResult<>(DEFERRED_TIMEOUT_MS);
        deferred.onTimeout(() -> deferred.setResult(ResponseEntity.ok(error(id, -32000, "tools/call timeout"))));

        final McpApiKeyAuthenticator.AuthContext auth;
        final String toolName;
        final ToolMapping mapping;
        final String argsJson;
        try {
            auth = authenticate(server, authorization);
            JsonNode params = message.path("params");
            toolName = text(params, "name");
            if (toolName == null || toolName.isBlank()) {
                throw new BusinessException("tools/call 缺少 name");
            }
            rateLimiter.checkOrThrow(auth.keyHash(), slug, toolName);
            mapping = toolRegistry.findMapping(slug, toolName)
                    .orElseThrow(() -> new BusinessException("工具不存在或不属于该 MCP: " + toolName));
            JsonNode arguments = params.path("arguments");
            argsJson = arguments.isMissingNode() || arguments.isNull()
                    ? "{}"
                    : objectMapper.writeValueAsString(arguments);
        } catch (BusinessException ex) {
            deferred.setResult(mapBusinessException(id, ex));
            return deferred;
        } catch (Exception ex) {
            deferred.setResult(ResponseEntity.ok(error(id, -32603, ex.getMessage())));
            return deferred;
        }

        long start = System.currentTimeMillis();
        httpToolForwarder.forwardAsync(mapping, argsJson, auth.rawToken())
                .subscribe(
                        output -> {
                            boolean success = output == null || !output.contains("\"error\"");
                            String errorMessage = success ? null : truncate(output, 500);
                            int duration = (int) (System.currentTimeMillis() - start);
                            auditService.recordAsync(
                                    slug,
                                    auth.keyHash(),
                                    auth.subject(),
                                    toolName,
                                    argsJson,
                                    success,
                                    errorMessage,
                                    duration);

                            ObjectNode response = objectMapper.createObjectNode();
                            response.put("jsonrpc", "2.0");
                            response.set("id", id);
                            ObjectNode result = response.putObject("result");
                            ArrayNode content = result.putArray("content");
                            ObjectNode textNode = content.addObject();
                            textNode.put("type", "text");
                            textNode.put("text", output == null ? "" : output);
                            result.put("isError", !success);
                            deferred.setResult(ResponseEntity.ok(response));
                        },
                        error -> deferred.setResult(ResponseEntity.ok(error(id, -32603, error.getMessage()))));
        return deferred;
    }

    private ResponseEntity<?> validateHeaders(
            JsonNode id,
            String method,
            JsonNode message,
            String mcpMethod,
            String mcpName) {
        if (mcpMethod != null && !mcpMethod.isBlank() && !mcpMethod.equals(method)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error(id, HEADER_MISMATCH, "HeaderMismatch: Mcp-Method != body.method"));
        }
        if ("tools/call".equals(method) || "resources/read".equals(method) || "prompts/get".equals(method)) {
            String bodyName = text(message.path("params"), "name");
            if (bodyName == null) {
                bodyName = text(message.path("params"), "uri");
            }
            if (mcpName != null && !mcpName.isBlank()) {
                if (bodyName == null || !mcpName.equals(bodyName)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(error(id, HEADER_MISMATCH, "HeaderMismatch: Mcp-Name != params.name/uri"));
                }
            }
        }
        return null;
    }

    private McpApiKeyAuthenticator.AuthContext authenticate(McpServerEntity server, String authorization) {
        return apiKeyAuthenticator.requireKeyForTools(server, authorization);
    }

    private ObjectNode initializeResult(McpServerEntity server) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", server.getName());
        serverInfo.put("version", "1.0.0");
        return result;
    }

    private ObjectNode toolsListResult(String slug) throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        for (ToolMapping mapping : toolRegistry.listBySlug(slug)) {
            ObjectNode tool = tools.addObject();
            tool.put("name", mapping.getToolName());
            tool.put("description", mapping.getDescription());
            tool.set("inputSchema", objectMapper.readTree(mapping.getInputSchemaJson()));
        }
        return result;
    }

    private McpServerEntity requirePublished(String slug) {
        return toolRegistry.findPublishedServer(slug)
                .orElseThrow(() -> new BusinessException(
                        "MCP Server 不存在或未发布: " + slug + "，请先在管理台发布"));
    }

    private ResponseEntity<?> mapBusinessException(JsonNode id, BusinessException ex) {
        if ("UNAUTHORIZED".equals(ex.getCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(id, -32001, ex.getMessage()));
        }
        if ("RATE_LIMITED".equals(ex.getCode())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error(id, -32029, ex.getMessage()));
        }
        return ResponseEntity.ok(error(id, -32603, ex.getMessage()));
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        } else {
            response.putNull("id");
        }
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "error" : message);
        return response;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
