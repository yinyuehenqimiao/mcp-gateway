package com.mcp.gateway.service.ratelimit;

import com.mcp.gateway.common.exception.BusinessException;
import com.mcp.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的简单滑动窗口近似限流（固定 60s 桶）。
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties gatewayProperties;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, GatewayProperties gatewayProperties) {
        this.redisTemplate = redisTemplate;
        this.gatewayProperties = gatewayProperties;
    }

    public void checkOrThrow(String keyHash, String slug, String toolName) {
        GatewayProperties.RateLimit cfg = gatewayProperties.getRateLimit();
        if (!cfg.isEnabled()) {
            return;
        }
        try {
            long keyCount = incr("rl:key:" + keyHash);
            if (keyCount > cfg.getPerKeyPerMinute()) {
                throw new BusinessException(
                        "RATE_LIMITED",
                        "调用方限流：每分钟最多 " + cfg.getPerKeyPerMinute()
                                + " 次（当前 " + keyCount + "）");
            }
            if (toolName != null && !toolName.isBlank()) {
                long toolCount = incr("rl:tool:" + slug + ":" + toolName);
                if (toolCount > cfg.getPerToolPerMinute()) {
                    throw new BusinessException(
                            "RATE_LIMITED",
                            "工具限流：" + toolName + " 每分钟最多 " + cfg.getPerToolPerMinute()
                                    + " 次（当前 " + toolCount + "）");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // Redis 不可用时降级放行，避免拖垮 MCP
            log.warn("Redis 限流检查失败，已降级放行: {}", ex.getMessage());
        }
    }

    private long incr(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count == null ? 0L : count;
    }
}
