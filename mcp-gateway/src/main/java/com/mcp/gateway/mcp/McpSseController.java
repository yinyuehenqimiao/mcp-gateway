package com.mcp.gateway.mcp;

import com.mcp.gateway.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 按 slug 隔离的 MCP SSE 入口：
 * GET  /mcp/{slug}/sse
 * POST /mcp/{slug}/message?sessionId=...
 */
@RestController
@RequestMapping("/mcp/{slug}")
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final long DEFERRED_TIMEOUT_MS = 30_000L;

    private final DynamicToolRegistry toolRegistry;
    private final HttpToolForwarder httpToolForwarder;
    private final McpSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final McpApiKeyAuthenticator apiKeyAuthenticator;
    private final RedisRateLimiter rateLimiter;
    private final ToolCallAuditService auditService;

    public McpSseController(
            DynamicToolRegistry toolRegistry,
            HttpToolForwarder httpToolForwarder,
            McpSessionManager sessionManager,
            ObjectMapper objectMapper,
            McpApiKeyAuthenticator apiKeyAuthenticator,
            RedisRateLimiter rateLimiter,
            ToolCallAuditService auditService) {
        this.toolRegistry = toolRegistry;
        this.httpToolForwarder = httpToolForwarder;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.apiKeyAuthenticator = apiKeyAuthenticator;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @GetMapping(value = "/sse")
    public Object connect(
            @PathVariable String slug,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        final McpServerEntity server;
        final McpApiKeyAuthenticator.AuthContext auth;
        try {
            server = requirePublished(slug);
            auth = apiKeyAuthenticator.authenticate(server, authorization);
        } catch (BusinessException ex) {
            HttpStatus status = "UNAUTHORIZED".equals(ex.getCode())
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "code", ex.getCode() == null ? "BAD_REQUEST" : ex.getCode(),
                            "message", ex.getMessage() == null ? "error" : ex.getMessage()));
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        McpSession session = sessionManager.create(sessionId, slug, auth.rawToken(), emitter);

        try {
            String endpoint = "/mcp/" + slug + "/message?sessionId=" + sessionId;
            // endpoint 事件 data 必须是纯路径字符串（非 JSON），供 MCP 客户端解析 sessionId
            session.emitter().send(SseEmitter.event().name("endpoint").data(endpoint));
            log.info("MCP SSE connected slug={} sessionId={} subject={}", slug, sessionId, auth.subject());
        } catch (IOException ex) {
            sessionManager.remove(sessionId);
            emitter.completeWithError(ex);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    @PostMapping("/message")
    public Object message(
            @PathVariable String slug,
            @RequestParam String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body) throws Exception {
        McpServerEntity server = requirePublished(slug);

        McpSession session = sessionManager.get(sessionId)
                .orElseThrow(() -> new BusinessException("无效 sessionId，请先连接 /mcp/" + slug + "/sse"));
        if (!slug.equals(session.slug())) {
            throw new BusinessException("session 与 slug 不匹配");
        }

        JsonNode message = objectMapper.readTree(body);
        if (message.isArray()) {
            for (JsonNode item : message) {
                Object handled = handleOne(session, server, authorization, item);
                if (handled instanceof DeferredResult<?>) {
                    // 批量场景少见；同步等待不合适，改为逐条同步执行 tools/call
                    return ResponseEntity.badRequest().body(Map.of(
                            "code", "BAD_REQUEST",
                            "message", "批量 JSON-RPC 暂不支持含 tools/call 的异步批处理，请逐条发送"));
                }
            }
            return ResponseEntity.accepted().build();
        }
        return handleOne(session, server, authorization, message);
    }

    private Object handleOne(
            McpSession session,
            McpServerEntity server,
            String authorization,
            JsonNode message) throws Exception {
        String method = text(message, "method");
        JsonNode id = message.get("id");
        boolean isNotification = id == null || id.isNull();

        if (method == null || method.isBlank()) {
            return ResponseEntity.badRequest().body(error(id, -32600, "Invalid Request"));
        }

        if (isNotification) {
            log.debug("MCP notification slug={} method={}", session.slug(), method);
            return ResponseEntity.accepted().build();
        }

        if ("tools/call".equals(method)) {
            return handleToolsCallAsync(session, server, authorization, message, id);
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        try {
            switch (method) {
                case "initialize" -> response.set("result", initializeResult(server));
                case "ping" -> response.set("result", objectMapper.createObjectNode());
                case "tools/list" -> {
                    McpApiKeyAuthenticator.AuthContext auth = resolveToolAuth(server, session, authorization);
                    rateLimiter.checkOrThrow(auth.keyHash(), session.slug(), null);
                    response.set("result", toolsListResult(session.slug()));
                }
                default -> {
                    return ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
                }
            }
        } catch (BusinessException ex) {
            if ("UNAUTHORIZED".equals(ex.getCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(id, -32001, ex.getMessage()));
            }
            if ("RATE_LIMITED".equals(ex.getCode())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error(id, -32029, ex.getMessage()));
            }
            return ResponseEntity.ok(error(id, -32603, ex.getMessage()));
        } catch (Exception ex) {
            log.error("MCP handle failed slug={} method={}", session.slug(), method, ex);
            return ResponseEntity.ok(error(id, -32603, ex.getMessage()));
        }

        sendSseMessage(session, response);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private DeferredResult<ResponseEntity<?>> handleToolsCallAsync(
            McpSession session,
            McpServerEntity server,
            String authorization,
            JsonNode message,
            JsonNode id) {
        DeferredResult<ResponseEntity<?>> deferred = new DeferredResult<>(DEFERRED_TIMEOUT_MS);
        deferred.onTimeout(() -> deferred.setResult(
                ResponseEntity.ok(error(id, -32000, "tools/call timeout"))));

        final McpApiKeyAuthenticator.AuthContext auth;
        final String toolName;
        final ToolMapping mapping;
        final String argsJson;
        try {
            auth = resolveToolAuth(server, session, authorization);
            JsonNode params = message.path("params");
            toolName = text(params, "name");
            if (toolName == null || toolName.isBlank()) {
                throw new BusinessException("tools/call 缺少 name");
            }
            rateLimiter.checkOrThrow(auth.keyHash(), session.slug(), toolName);
            mapping = toolRegistry.findMapping(session.slug(), toolName)
                    .orElseThrow(() -> new BusinessException("工具不存在或不属于该 MCP: " + toolName));
            JsonNode arguments = params.path("arguments");
            argsJson = arguments.isMissingNode() || arguments.isNull()
                    ? "{}"
                    : objectMapper.writeValueAsString(arguments);
        } catch (BusinessException ex) {
            if ("UNAUTHORIZED".equals(ex.getCode())) {
                deferred.setResult(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(error(id, -32001, ex.getMessage())));
            } else if ("RATE_LIMITED".equals(ex.getCode())) {
                deferred.setResult(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(error(id, -32029, ex.getMessage())));
            } else {
                deferred.setResult(ResponseEntity.ok(error(id, -32603, ex.getMessage())));
            }
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
                                    session.slug(),
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
                            ObjectNode text = content.addObject();
                            text.put("type", "text");
                            text.put("text", output == null ? "" : output);
                            result.put("isError", !success);

                            try {
                                sendSseMessage(session, response);
                            } catch (Exception ex) {
                                log.warn("SSE 推送失败 session={}: {}", session.sessionId(), ex.getMessage());
                            }
                            deferred.setResult(ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(response));
                        },
                        error -> {
                            log.error("tools/call 异步失败 tool={}", toolName, error);
                            deferred.setResult(ResponseEntity.ok(error(id, -32603, error.getMessage())));
                        });
        return deferred;
    }

    private McpApiKeyAuthenticator.AuthContext resolveToolAuth(
            McpServerEntity server,
            McpSession session,
            String authorization) {
        String headerToken = McpApiKeyAuthenticator.extractToken(authorization);
        String effectiveAuth = (headerToken != null && !headerToken.isBlank())
                ? "Bearer " + headerToken
                : (session.apiKey() == null ? null : "Bearer " + session.apiKey());
        return apiKeyAuthenticator.requireKeyForTools(server, effectiveAuth);
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

    private void sendSseMessage(McpSession session, ObjectNode response) throws IOException {
        session.sendJsonEvent("message", objectMapper.writeValueAsString(response));
    }

    private McpServerEntity requirePublished(String slug) {
        return toolRegistry.findPublishedServer(slug)
                .orElseThrow(() -> new BusinessException(
                        "MCP Server 不存在或未发布: " + slug + "，请先在管理台发布"));
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
