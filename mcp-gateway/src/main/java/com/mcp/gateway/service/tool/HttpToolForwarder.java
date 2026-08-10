package com.mcp.gateway.service.tool;

import com.mcp.gateway.common.exception.BusinessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.service.openapi.ParsedOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

@Component
public class HttpToolForwarder {

    private static final Logger log = LoggerFactory.getLogger(HttpToolForwarder.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpToolForwarder(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String forward(ToolMapping mapping, String toolInputJson) {
        return forward(mapping, toolInputJson, null);
    }

    public String forward(ToolMapping mapping, String toolInputJson, String callerBearerToken) {
        try {
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
            String method = mapping.getHttpMethod();

            log.info("Forward tool={} -> {} {}", mapping.getToolName(), method, uri);

            ResponseEntity<String> response = switch (method) {
                case "GET" -> restClient.get().uri(uri).headers(h -> h.addAll(headers)).retrieve().toEntity(String.class);
                case "DELETE" -> restClient.delete().uri(uri).headers(h -> h.addAll(headers)).retrieve().toEntity(String.class);
                case "POST" -> restClient.post().uri(uri).headers(h -> h.addAll(headers))
                        .contentType(MediaType.APPLICATION_JSON).body(bodyToJson(bodyNode)).retrieve().toEntity(String.class);
                case "PUT" -> restClient.put().uri(uri).headers(h -> h.addAll(headers))
                        .contentType(MediaType.APPLICATION_JSON).body(bodyToJson(bodyNode)).retrieve().toEntity(String.class);
                case "PATCH" -> restClient.method(org.springframework.http.HttpMethod.PATCH).uri(uri)
                        .headers(h -> h.addAll(headers)).contentType(MediaType.APPLICATION_JSON)
                        .body(bodyToJson(bodyNode)).retrieve().toEntity(String.class);
                default -> throw new BusinessException("不支持的 HTTP 方法: " + method);
            };

            String responseBody = response.getBody();
            return responseBody == null ? "" : responseBody;
        } catch (Exception ex) {
            log.error("工具转发失败 tool={}", mapping.getToolName(), ex);
            return "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
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
            // 即使系统未配 auth，若调用方带了 JWT 也默认透传，方便 demo-biz 联调
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
}
