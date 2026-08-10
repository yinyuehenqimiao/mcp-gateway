package com.mcp.gateway.mcp;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpSessionManager {

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    public McpSession create(String sessionId, String slug, String apiKey, SseEmitter emitter) {
        McpSession session = new McpSession(sessionId, slug, apiKey, emitter);
        sessions.put(sessionId, session);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(ex -> sessions.remove(sessionId));
        return session;
    }

    public Optional<McpSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
