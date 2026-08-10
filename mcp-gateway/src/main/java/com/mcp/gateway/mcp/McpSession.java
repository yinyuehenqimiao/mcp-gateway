package com.mcp.gateway.mcp;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

record McpSession(String sessionId, String slug, SseEmitter emitter) {
}
