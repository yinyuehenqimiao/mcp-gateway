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
import java.util.Locale;

/**
 * MCP 接入鉴权：
 * - JWT 模式：校验业务登录 JWT（密钥可用 MCP 上配置的 jwtSecret，否则用网关默认）
 * - FIXED 模式：Authorization Bearer 必须等于 accessToken
 * 通过后，同一 token 可被业务系统「JWT 透传」转发给下游。
 */
@Component
public class McpApiKeyAuthenticator {

    private final GatewayProperties gatewayProperties;

    public McpApiKeyAuthenticator(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    public AuthContext authenticate(McpServerEntity server, String authorization) {
        return authenticateInternal(server, authorization, false);
    }

    public AuthContext requireKeyForTools(McpServerEntity server, String authorization) {
        return authenticateInternal(server, authorization, true);
    }

    private AuthContext authenticateInternal(McpServerEntity server, String authorization, boolean toolsRequired) {
        String rawToken = extractToken(authorization);
        String mode = normalizeMode(server.getAuthMode());

        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(
                    "UNAUTHORIZED",
                    "缺少凭证：请在 Authorization: Bearer <JWT或AccessToken> 中携带");
        }

        if ("FIXED".equals(mode)) {
            String expected = server.getAccessToken();
            if (expected == null || expected.isBlank()) {
                throw new BusinessException("UNAUTHORIZED", "该 MCP 为固定令牌模式，但未配置 Access Token");
            }
            if (!expected.equals(rawToken)) {
                throw new BusinessException("UNAUTHORIZED", "Access Token 不正确");
            }
            return new AuthContext(rawToken, hash(rawToken), subjectFromJwt(rawToken, resolveSecret(server)), true);
        }

        // JWT 模式
        if (!gatewayProperties.getJwt().isEnabled()) {
            throw new BusinessException("UNAUTHORIZED", "网关未开启 JWT 校验（gateway.jwt.enabled=false）");
        }
        String secret = resolveSecret(server);
        try {
            Claims claims = parseJwt(rawToken, secret);
            return new AuthContext(rawToken, hash(rawToken), claims.getSubject(), true);
        } catch (Exception ex) {
            throw new BusinessException(
                    "UNAUTHORIZED",
                    "JWT 无效或密钥不匹配。请确认：1) Agent 使用业务登录 JWT；2) MCP 的 JWT 密钥与下游签发密钥一致"
                            + (toolsRequired ? "；tools/list、tools/call 必须带有效 JWT" : "")
                            + "。原因: " + ex.getMessage());
        }
    }

    private String resolveSecret(McpServerEntity server) {
        if (server.getJwtSecret() != null && !server.getJwtSecret().isBlank()) {
            return server.getJwtSecret().trim();
        }
        return gatewayProperties.getJwt().getSecret();
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "JWT";
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        return "FIXED".equals(m) ? "FIXED" : "JWT";
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

    private Claims parseJwt(String token, String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // JJWT 要求 HS256 key 足够长；过短时做填充，避免启动后才炸
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String subjectFromJwt(String token, String secret) {
        try {
            return parseJwt(token, secret).getSubject();
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
