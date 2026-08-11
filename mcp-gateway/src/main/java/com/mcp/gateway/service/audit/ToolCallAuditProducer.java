package com.mcp.gateway.service.audit;

import com.mcp.gateway.config.GatewayProperties;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工具调用审计 MQ 生产者。发送失败仅记日志，不阻断 tools/call。
 * Topic 默认 mcp-audit_topic，与短链 short-link-stats_topic 隔离。
 */
@Component
@ConditionalOnProperty(prefix = "gateway.audit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolCallAuditProducer {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final GatewayProperties gatewayProperties;

    public ToolCallAuditProducer(RocketMQTemplate rocketMQTemplate, GatewayProperties gatewayProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.gatewayProperties = gatewayProperties;
    }

    public void send(Map<String, String> fields) {
        GatewayProperties.Audit.Mq mq = gatewayProperties.getAudit().getMq();
        Map<String, String> payload = new HashMap<>();
        if (fields != null) {
            fields.forEach((k, v) -> {
                if (k != null && v != null) {
                    payload.put(k, v);
                }
            });
        }
        String keys = payload.get("keys");
        if (keys == null || keys.isBlank()) {
            keys = UUID.randomUUID().toString();
            payload.put("keys", keys);
        }
        try {
            Message<Map<String, String>> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MessageConst.PROPERTY_KEYS, keys)
                    .build();
            SendResult result = rocketMQTemplate.syncSend(mq.getTopic(), message, mq.getSendTimeoutMs());
            log.debug("audit mq sent topic={} msgId={} keys={}",
                    mq.getTopic(), result.getMsgId(), keys);
        } catch (Exception ex) {
            log.warn("audit mq send failed topic={} keys={}: {}",
                    mq.getTopic(), keys, ex.getMessage());
        }
    }
}
