package com.mcp.gateway.mcp;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MCP SSE 会话。SseEmitter 非线程安全，同 session 并发 tools/call 必须串行 send。
 */
public final class McpSession {

    private final String sessionId;
    private final String slug;
    private final String apiKey;
    private final SseEmitter emitter;
    private final ReentrantLock sendLock = new ReentrantLock();

    public McpSession(String sessionId, String slug, String apiKey, SseEmitter emitter) {
        this.sessionId = sessionId;
        this.slug = slug;
        this.apiKey = apiKey;
        this.emitter = emitter;
    }

    public String sessionId() {
        return sessionId;
    }

    public String slug() {
        return slug;
    }

    public String apiKey() {
        return apiKey;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void sendJsonEvent(String eventName, String json) throws IOException {
        sendLock.lock();
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON));
        } finally {
            sendLock.unlock();
        }
    }
}
