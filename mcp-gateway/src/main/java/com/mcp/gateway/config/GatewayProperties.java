package com.mcp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String defaultServerSlug = "default";
    private long httpTimeoutMs = 15000L;

    public String getDefaultServerSlug() {
        return defaultServerSlug;
    }

    public void setDefaultServerSlug(String defaultServerSlug) {
        this.defaultServerSlug = defaultServerSlug;
    }

    public long getHttpTimeoutMs() {
        return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(long httpTimeoutMs) {
        this.httpTimeoutMs = httpTimeoutMs;
    }
}
