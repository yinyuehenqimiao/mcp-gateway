package com.mcp.gateway.service.auth;

import com.mcp.gateway.common.exception.BusinessException;
import com.mcp.gateway.config.GatewayProperties;
import com.mcp.gateway.domain.entity.McpServerEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * MCP API Key 校验：
 * 1) 静态 accessToken 精确匹配；
 * 2) 或 gateway.jwt 开启时，接受与 demo-biz 同密钥签发的 JWT。
 */
@Component
public class McpApiKeyAuthenticator {

    private final GatewayProperties gatewayProperties;
    private final SecretKey jwtKey;

    public McpApiKeyAuthenticator(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.jwtKey = Keys.hmacShaKeyFor(
                gatewayProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public AuthContext authenticate(McpServerEntity server, String authorization) {
        String expected = server.getAccessToken();
        boolean requireKey = expected != null && !expected.isBlank();
        String rawToken = extractToken(authorization);

        if (!requireKey) {
            // 未配置 accessToken：开放；仍可记录调用方
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken), false);
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "缺少 API Key：请在 Authorization: Bearer <token> 中携带");
        }

        if (expected.equals(rawToken)) {
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken), true);
        }

        if (gatewayProperties.getJwt().isEnabled() && isValidJwt(rawToken)) {
            // 配置了 accessToken，同时允许同密钥 JWT（便于把下游登录令牌直接当 MCP Key）
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken), true);
        }

        throw new BusinessException("UNAUTHORIZED", "API Key / JWT 无效");
    }

    /**
     * tools/list、tools/call 必须有 Key：即使未配置静态 accessToken，也要求携带可校验的 JWT（jwt.enabled 时）。
     */
    public AuthContext requireKeyForTools(McpServerEntity server, String authorization) {
        String expected = server.getAccessToken();
        boolean hasStatic = expected != null && !expected.isBlank();
        String rawToken = extractToken(authorization);

        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "tools/list 与 tools/call 需要 API Key（Authorization Bearer）");
        }

        if (hasStatic && expected.equals(rawToken)) {
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken), true);
        }
        if (gatewayProperties.getJwt().isEnabled() && isValidJwt(rawToken)) {
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken), true);
        }
        if (hasStatic) {
            throw new BusinessException("UNAUTHORIZED", "API Key / JWT 无效");
        }
        throw new BusinessException("UNAUTHORIZED", "JWT 无效或已过期");
    }

    public static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private boolean isValidJwt(String token) {
        try {
            parseJwt(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parseJwt(String token) {
        return Jwts.parser()
                .verifyWith(jwtKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String subjectFromJwt(String token) {
        if (token == null || token.isBlank() || !gatewayProperties.getJwt().isEnabled()) {
            return null;
        }
        try {
            return parseJwt(token).getSubject();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "anonymous";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 32);
        } catch (Exception ex) {
            return "unknown";
        }
    }

    public record AuthContext(String rawToken, String keyHash, String subject, boolean authenticated) {
    }
}
