package com.demo.biz.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.jwt")
public class JwtProperties {

    /** HS256 密钥，需与 mcp-gateway.gateway.jwt.secret 一致以便联调 */
    private String secret = "demo-biz-jwt-secret-change-me-32bytes!!";
    private long expireSeconds = 7200;
    private String demoUsername = "demo";
    private String demoPassword = "demo123";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public String getDemoUsername() {
        return demoUsername;
    }

    public void setDemoUsername(String demoUsername) {
        this.demoUsername = demoUsername;
    }

    public String getDemoPassword() {
        return demoPassword;
    }

    public void setDemoPassword(String demoPassword) {
        this.demoPassword = demoPassword;
    }
}
