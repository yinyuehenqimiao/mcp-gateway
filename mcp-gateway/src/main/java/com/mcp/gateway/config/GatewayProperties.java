package com.mcp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String defaultServerSlug = "default";
    /** 对外展示的网关根地址（SSE / message 链接） */
    private String publicBaseUrl = "http://localhost:18190";
    private long httpTimeoutMs = 15000L;
    private final Jwt jwt = new Jwt();
    private final RateLimit rateLimit = new RateLimit();
    private final Audit audit = new Audit();

    public String getDefaultServerSlug() {
        return defaultServerSlug;
    }

    public void setDefaultServerSlug(String defaultServerSlug) {
        this.defaultServerSlug = defaultServerSlug;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getHttpTimeoutMs() {
        return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(long httpTimeoutMs) {
        this.httpTimeoutMs = httpTimeoutMs;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Audit getAudit() {
        return audit;
    }

    public static class Jwt {
        /** 开启后：除静态 accessToken 外，也接受与 demo-biz 同密钥签发的 JWT */
        private boolean enabled = true;
        private String secret = "demo-biz-jwt-secret-change-me-32bytes!!";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        /** 同一调用方 Key 每分钟上限 */
        private int perKeyPerMinute = 60;
        /** 同一 slug+tool 每分钟上限 */
        private int perToolPerMinute = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPerKeyPerMinute() {
            return perKeyPerMinute;
        }

        public void setPerKeyPerMinute(int perKeyPerMinute) {
            this.perKeyPerMinute = perKeyPerMinute;
        }

        public int getPerToolPerMinute() {
            return perToolPerMinute;
        }

        public void setPerToolPerMinute(int perToolPerMinute) {
            this.perToolPerMinute = perToolPerMinute;
        }
    }

    public static class Audit {
        private final Mq mq = new Mq();

        public Mq getMq() {
            return mq;
        }

        public static class Mq {
            private boolean enabled = true;
            private String topic = "mcp-audit_topic";
            private String consumerGroup = "mcp-audit_cg";
            private long sendTimeoutMs = 2000L;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTopic() {
                return topic;
            }

            public void setTopic(String topic) {
                this.topic = topic;
            }

            public String getConsumerGroup() {
                return consumerGroup;
            }

            public void setConsumerGroup(String consumerGroup) {
                this.consumerGroup = consumerGroup;
            }

            public long getSendTimeoutMs() {
                return sendTimeoutMs;
            }

            public void setSendTimeoutMs(long sendTimeoutMs) {
                this.sendTimeoutMs = sendTimeoutMs;
            }
        }
    }
}
