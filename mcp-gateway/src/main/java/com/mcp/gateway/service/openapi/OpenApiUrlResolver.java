package com.mcp.gateway.service.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 swagger-ui / 文档页 URL 归一化为可拉取的 OpenAPI JSON 地址。
 */
@Component
public class OpenApiUrlResolver {

    private static final Pattern CONFIG_URL = Pattern.compile(
            "configUrl\\s*[:=]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_IN_CONFIG = Pattern.compile(
            "[\"']url[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public OpenApiUrlResolver(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public String resolveDocumentUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException("OpenAPI URL 为空");
        }
        String input = rawUrl.trim();
        URI base;
        try {
            base = URI.create(input);
        } catch (Exception ex) {
            throw new BusinessException("OpenAPI URL 非法: " + rawUrl);
        }
        if (base.getScheme() == null || base.getHost() == null) {
            throw new BusinessException("OpenAPI URL 需包含协议与主机，例如 http://localhost:8001/v3/api-docs");
        }

        // 已是 api-docs 类地址
        String path = base.getPath() == null ? "" : base.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("api-docs") || path.endsWith(".json") || path.contains("openapi")) {
            return stripFragment(input);
        }

        // swagger-ui 页面：探测常见 api-docs，并尝试读 swagger-config
        if (path.contains("swagger-ui") || path.endsWith("/swagger") || path.contains("swagger-ui.html")) {
            for (String candidate : buildCandidates(base)) {
                if (looksLikeOpenApiJson(candidate)) {
                    return candidate;
                }
            }
            throw new BusinessException(
                    "已识别为 Swagger UI 地址，但未能自动找到 OpenAPI JSON。"
                            + " 请改用例如: " + origin(base) + "/v3/api-docs");
        }

        // 其他地址：仍尝试一组候选
        for (String candidate : buildCandidates(base)) {
            if (looksLikeOpenApiJson(candidate)) {
                return candidate;
            }
        }
        return stripFragment(input);
    }

    private List<String> buildCandidates(URI base) {
        Set<String> candidates = new LinkedHashSet<>();
        String origin = origin(base);

        // 1) swagger-config
        tryFetchSwaggerConfigUrls(origin).forEach(candidates::add);

        candidates.add(origin + "/v3/api-docs");
        candidates.add(origin + "/v3/api-docs/default");
        candidates.add(origin + "/api-docs");
        candidates.add(origin + "/v2/api-docs");
        candidates.add(origin + "/swagger/v1/swagger.json");
        candidates.add(origin + "/openapi.json");

        // 从 HTML 里抠 configUrl / url
        tryParseSwaggerUiHtml(stripFragment(base.toString())).forEach(candidates::add);

        return new ArrayList<>(candidates);
    }

    private List<String> tryFetchSwaggerConfigUrls(String origin) {
        List<String> urls = new ArrayList<>();
        for (String configPath : List.of("/v3/api-docs/swagger-config", "/api-docs/swagger-config")) {
            String configUrl = origin + configPath;
            try {
                String body = restClientBuilder.build().get().uri(configUrl).retrieve().body(String.class);
                if (body == null || body.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(body);
                String url = text(node.get("url"));
                if (url != null && !url.isBlank()) {
                    urls.add(absolutize(origin, url));
                }
                JsonNode urlsNode = node.get("urls");
                if (urlsNode != null && urlsNode.isArray()) {
                    for (JsonNode item : urlsNode) {
                        String u = text(item.get("url"));
                        if (u != null && !u.isBlank()) {
                            urls.add(absolutize(origin, u));
                        }
                    }
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return urls;
    }

    private List<String> tryParseSwaggerUiHtml(String htmlUrl) {
        List<String> urls = new ArrayList<>();
        try {
            String html = restClientBuilder.build().get().uri(htmlUrl).retrieve().body(String.class);
            if (html == null || html.isBlank()) {
                return urls;
            }
            Matcher configMatcher = CONFIG_URL.matcher(html);
            String origin = origin(URI.create(htmlUrl));
            while (configMatcher.find()) {
                String configUrl = absolutize(origin, configMatcher.group(1));
                tryFetchSwaggerConfigUrlsFromAbsolute(configUrl).forEach(urls::add);
            }
            Matcher urlMatcher = URL_IN_CONFIG.matcher(html);
            while (urlMatcher.find()) {
                urls.add(absolutize(origin, urlMatcher.group(1)));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return urls;
    }

    private List<String> tryFetchSwaggerConfigUrlsFromAbsolute(String configUrl) {
        List<String> urls = new ArrayList<>();
        try {
            String body = restClientBuilder.build().get().uri(configUrl).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return urls;
            }
            JsonNode node = objectMapper.readTree(body);
            String origin = origin(URI.create(configUrl));
            String url = text(node.get("url"));
            if (url != null && !url.isBlank()) {
                urls.add(absolutize(origin, url));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return urls;
    }

    private boolean looksLikeOpenApiJson(String url) {
        try {
            String body = restClientBuilder.build().get().uri(url).retrieve().body(String.class);
            if (body == null) {
                return false;
            }
            String trimmed = body.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                return false;
            }
            JsonNode root = objectMapper.readTree(trimmed);
            return root.has("openapi") || root.has("swagger") || root.has("paths");
        } catch (Exception ex) {
            return false;
        }
    }

    private static String origin(URI uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() > 0) {
            sb.append(':').append(uri.getPort());
        }
        return sb.toString();
    }

    private static String absolutize(String origin, String maybeRelative) {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
            return maybeRelative;
        }
        if (maybeRelative.startsWith("/")) {
            return origin + maybeRelative;
        }
        return origin + "/" + maybeRelative;
    }

    private static String stripFragment(String url) {
        int idx = url.indexOf('#');
        return idx >= 0 ? url.substring(0, idx) : url;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
