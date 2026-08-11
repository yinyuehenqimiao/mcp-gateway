package com.mcp.gateway.service.tool;

import com.mcp.gateway.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.service.openapi.ParsedOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

@Component
public class HttpToolForwarder {

    private static final Logger log = LoggerFactory.getLogger(HttpToolForwarder.class);

    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public HttpToolForwarder(
            RestClient.Builder restClientBuilder,
            @Qualifier("toolForwardWebClient") WebClient toolForwardWebClient,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.webClient = toolForwardWebClient;
        this.objectMapper = objectMapper;
    }

    public String forward(ToolMapping mapping, String toolInputJson) {
        return forward(mapping, toolInputJson, null);
    }

    /** 管理台试调用等同步场景 */
    public String forward(ToolMapping mapping, String toolInputJson, String callerBearerToken) {
        try {
            PreparedRequest prepared = prepare(mapping, toolInputJson, callerBearerToken);
            log.debug("Forward(sync) tool={} -> {} {}", mapping.getToolName(), prepared.method(), prepared.uri());

            ResponseEntity<String> response = switch (prepared.method()) {
                case "GET" -> restClient.get().uri(prepared.uri()).headers(h -> h.addAll(prepared.headers()))
                        .retrieve().toEntity(String.class);
                case "DELETE" -> restClient.delete().uri(prepared.uri()).headers(h -> h.addAll(prepared.headers()))
                        .retrieve().toEntity(String.class);
                case "POST" -> restClient.post().uri(prepared.uri()).headers(h -> h.addAll(prepared.headers()))
                        .contentType(MediaType.APPLICATION_JSON).body(prepared.bodyJson())
                        .retrieve().toEntity(String.class);
                case "PUT" -> restClient.put().uri(prepared.uri()).headers(h -> h.addAll(prepared.headers()))
                        .contentType(MediaType.APPLICATION_JSON).body(prepared.bodyJson())
                        .retrieve().toEntity(String.class);
                case "PATCH" -> restClient.method(HttpMethod.PATCH).uri(prepared.uri())
                        .headers(h -> h.addAll(prepared.headers())).contentType(MediaType.APPLICATION_JSON)
                        .body(prepared.bodyJson()).retrieve().toEntity(String.class);
                default -> throw new BusinessException("不支持的 HTTP 方法: " + prepared.method());
            };
            String responseBody = response.getBody();
            return responseBody == null ? "" : responseBody;
        } catch (BusinessException ex) {
            return "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}";
        } catch (Exception ex) {
            log.error("工具转发失败 tool={}", mapping.getToolName(), ex);
            return "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    /** MCP tools/call 热路径：非阻塞出站 */
    public Mono<String> forwardAsync(ToolMapping mapping, String toolInputJson, String callerBearerToken) {
        try {
            PreparedRequest prepared = prepare(mapping, toolInputJson, callerBearerToken);
            log.debug("Forward(async) tool={} -> {} {}", mapping.getToolName(), prepared.method(), prepared.uri());

            WebClient.RequestBodySpec spec = webClient.method(HttpMethod.valueOf(prepared.method()))
                    .uri(prepared.uri())
                    .headers(h -> h.addAll(prepared.headers()));

            Mono<String> mono;
            if ("POST".equals(prepared.method()) || "PUT".equals(prepared.method()) || "PATCH".equals(prepared.method())) {
                mono = spec.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(prepared.bodyJson() == null ? "{}" : prepared.bodyJson())
                        .retrieve()
                        .bodyToMono(String.class);
            } else {
                mono = spec.retrieve().bodyToMono(String.class);
            }
            return mono.defaultIfEmpty("")
                    .onErrorResume(ex -> {
                        log.error("异步工具转发失败 tool={}", mapping.getToolName(), ex);
                        return Mono.just("{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
                    });
        } catch (BusinessException ex) {
            return Mono.just("{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            log.error("异步工具转发准备失败 tool={}", mapping.getToolName(), ex);
            return Mono.just("{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private PreparedRequest prepare(ToolMapping mapping, String toolInputJson, String callerBearerToken) throws Exception {
        JsonNode args = (toolInputJson == null || toolInputJson.isBlank())
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(toolInputJson);

        String path = mapping.getPathTemplate();
        for (ParsedOperation.Param parameter : mapping.pathParameters()) {
            JsonNode value = args.get(parameter.name());
            if (value == null || value.isNull()) {
                throw new BusinessException("缺少 path 参数: " + parameter.name());
            }
            path = path.replace("{" + parameter.name() + "}", value.asText());
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(trimTrailingSlash(mapping.getBaseUrl()) + ensureLeadingSlash(path));

        for (ParsedOperation.Param parameter : mapping.queryParameters()) {
            JsonNode value = args.get(parameter.name());
            if (value == null || value.isNull()) {
                if (parameter.required()) {
                    throw new BusinessException("缺少 query 参数: " + parameter.name());
                }
                continue;
            }
            uriBuilder.queryParam(parameter.name(), value.asText());
        }

        URI uri = uriBuilder.build(true).toUri();
        HttpHeaders headers = buildHeaders(mapping, args, callerBearerToken);
        JsonNode bodyNode = args.get("body");
        return new PreparedRequest(mapping.getHttpMethod(), uri, headers, bodyToJson(bodyNode));
    }

    private HttpHeaders buildHeaders(ToolMapping mapping, JsonNode args, String callerBearerToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE + ",*/*"));

        if (mapping.getHeadersJson() != null && !mapping.getHeadersJson().isBlank()) {
            JsonNode staticHeaders = objectMapper.readTree(mapping.getHeadersJson());
            if (staticHeaders.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = staticHeaders.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    headers.set(entry.getKey(), entry.getValue().asText());
                }
            }
        }

        for (ParsedOperation.Param parameter : mapping.headerParameters()) {
            JsonNode value = args.get(parameter.name());
            if (value == null || value.isNull()) {
                if (parameter.required()) {
                    throw new BusinessException("缺少 header 参数: " + parameter.name());
                }
                continue;
            }
            headers.set(parameter.name(), value.asText());
        }

        applyAuth(mapping, headers, callerBearerToken);
        return headers;
    }

    private void applyAuth(ToolMapping mapping, HttpHeaders headers, String callerBearerToken) throws Exception {
        String authType = mapping.getAuthType();
        if (authType == null || "NONE".equalsIgnoreCase(authType)) {
            if (callerBearerToken != null && !callerBearerToken.isBlank()) {
                headers.setBearerAuth(callerBearerToken);
            }
            return;
        }
        if (mapping.getAuthConfig() == null || mapping.getAuthConfig().isBlank()) {
            if (callerBearerToken != null && !callerBearerToken.isBlank()) {
                headers.setBearerAuth(callerBearerToken);
            }
            return;
        }
        JsonNode config = objectMapper.readTree(mapping.getAuthConfig());
        boolean useCallerToken = config.path("useCallerToken").asBoolean(false);

        if ("BEARER".equalsIgnoreCase(authType)) {
            if (useCallerToken) {
                if (callerBearerToken == null || callerBearerToken.isBlank()) {
                    throw new BusinessException("下游要求透传调用方 Bearer，但未提供 API Key/JWT");
                }
                headers.setBearerAuth(callerBearerToken);
            } else {
                String token = config.path("token").asText(null);
                if (token != null && !token.isBlank()) {
                    headers.setBearerAuth(token);
                } else if (callerBearerToken != null && !callerBearerToken.isBlank()) {
                    headers.setBearerAuth(callerBearerToken);
                }
            }
        } else if ("API_KEY".equalsIgnoreCase(authType)) {
            String headerName = config.path("headerName").asText("X-API-Key");
            if (useCallerToken) {
                headers.set(headerName, callerBearerToken);
            } else {
                String apiKey = config.path("apiKey").asText(null);
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.set(headerName, apiKey);
                }
            }
        } else if ("BASIC".equalsIgnoreCase(authType)) {
            String username = config.path("username").asText("");
            String password = config.path("password").asText("");
            headers.setBasicAuth(username, password);
        }
    }

    private String bodyToJson(JsonNode bodyNode) throws Exception {
        if (bodyNode == null || bodyNode.isNull()) {
            return "{}";
        }
        if (bodyNode.isTextual()) {
            return bodyNode.asText();
        }
        return objectMapper.writeValueAsString(bodyNode);
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String ensureLeadingSlash(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String escapeJson(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record PreparedRequest(String method, URI uri, HttpHeaders headers, String bodyJson) {
    }
}
