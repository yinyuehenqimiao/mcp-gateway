package com.mcp.gateway.mcp;

import com.mcp.gateway.common.exception.BusinessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.gateway.domain.entity.McpServerEntity;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import com.mcp.gateway.service.tool.HttpToolForwarder;
import com.mcp.gateway.service.tool.ToolMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
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

    private final DynamicToolRegistry toolRegistry;
    private final HttpToolForwarder httpToolForwarder;
    private final McpSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public McpSseController(
            DynamicToolRegistry toolRegistry,
            HttpToolForwarder httpToolForwarder,
            McpSessionManager sessionManager,
            ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.httpToolForwarder = httpToolForwarder;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @PathVariable String slug,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        McpServerEntity server = requirePublished(slug);
        assertToken(server, authorization);

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        sessionManager.create(sessionId, slug, emitter);

        try {
            String endpoint = "/mcp/" + slug + "/message?sessionId=" + sessionId;
            emitter.send(SseEmitter.event().name("endpoint").data(endpoint));
            log.info("MCP SSE connected slug={} sessionId={}", slug, sessionId);
        } catch (IOException ex) {
            sessionManager.remove(sessionId);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @PostMapping("/message")
    public ResponseEntity<?> message(
            @PathVariable String slug,
            @RequestParam String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body) throws Exception {
        McpServerEntity server = requirePublished(slug);
        assertToken(server, authorization);

        McpSession session = sessionManager.get(sessionId)
                .orElseThrow(() -> new BusinessException("无效 sessionId，请先连接 /mcp/" + slug + "/sse"));
        if (!slug.equals(session.slug())) {
            throw new IllegalArgumentException("session 与 slug 不匹配");
        }

        JsonNode message = objectMapper.readTree(body);
        if (message.isArray()) {
            for (JsonNode item : message) {
                handleOne(session, server, item);
            }
            return ResponseEntity.accepted().build();
        }
        return handleOne(session, server, message);
    }

    private ResponseEntity<?> handleOne(McpSession session, McpServerEntity server, JsonNode message) throws Exception {
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

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        try {
            switch (method) {
                case "initialize" -> response.set("result", initializeResult(server));
                case "ping" -> response.set("result", objectMapper.createObjectNode());
                case "tools/list" -> response.set("result", toolsListResult(session.slug()));
                case "tools/call" -> response.set("result", toolsCallResult(session.slug(), message.path("params")));
                default -> {
                    return ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
                }
            }
        } catch (Exception ex) {
            log.error("MCP handle failed slug={} method={}", session.slug(), method, ex);
            return ResponseEntity.ok(error(id, -32603, ex.getMessage()));
        }

        sendSseMessage(session, response);
        // 同时在 HTTP 响应体返回，兼容部分客户端
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
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

    private ObjectNode toolsCallResult(String slug, JsonNode params) {
        String toolName = text(params, "name");
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("tools/call 缺少 name");
        }
        ToolMapping mapping = toolRegistry.findMapping(slug, toolName)
                .orElseThrow(() -> new BusinessException("工具不存在或不属于该 MCP: " + toolName));

        JsonNode arguments = params.path("arguments");
        String argsJson;
        try {
            argsJson = arguments.isMissingNode() || arguments.isNull()
                    ? "{}"
                    : objectMapper.writeValueAsString(arguments);
        } catch (Exception ex) {
            throw new IllegalArgumentException("arguments 解析失败", ex);
        }

        String output = httpToolForwarder.forward(mapping, argsJson);
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", output == null ? "" : output);
        result.put("isError", output != null && output.contains("\"error\""));
        return result;
    }

    private void sendSseMessage(McpSession session, ObjectNode response) throws IOException {
        session.emitter().send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
    }

    private McpServerEntity requirePublished(String slug) {
        return toolRegistry.findPublishedServer(slug)
                .orElseThrow(() -> new BusinessException(
                        "MCP Server 不存在或未发布: " + slug + "，请先在管理台发布"));
    }

    private void assertToken(McpServerEntity server, String authorization) {
        String expected = server.getAccessToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        // 本地学习默认不强制鉴权；只有客户端显式带了 Authorization 时才校验
        if (authorization == null || authorization.isBlank()) {
            log.debug("MCP slug={} 未携带 Authorization，已放行（accessToken 已配置但未强制）", server.getSlug());
            return;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : authorization.trim();
        if (!expected.equals(token)) {
            throw new IllegalArgumentException("Unauthorized：accessToken 不正确");
        }
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
}
