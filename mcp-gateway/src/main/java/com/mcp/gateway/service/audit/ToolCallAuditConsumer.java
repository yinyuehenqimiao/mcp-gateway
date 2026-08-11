package com.mcp.gateway.service.audit;

import com.mcp.gateway.domain.entity.ToolCallAudit;
import com.mcp.gateway.domain.repository.ToolCallAuditRepository;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 消费 mcp-audit_topic，异步写入 tool_call_audit。
 */
@Component
@ConditionalOnProperty(prefix = "gateway.audit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${gateway.audit.mq.topic:mcp-audit_topic}",
        consumerGroup = "${gateway.audit.mq.consumer-group:mcp-audit_cg}"
)
public class ToolCallAuditConsumer implements RocketMQListener<Map<String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditConsumer.class);

    private final ToolCallAuditRepository repository;

    public ToolCallAuditConsumer(ToolCallAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void onMessage(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        try {
            ToolCallAudit audit = new ToolCallAudit();
            audit.setSlug(payload.get("slug"));
            audit.setCallerKeyHash(payload.get("callerKeyHash"));
            audit.setCallerSubject(emptyToNull(payload.get("callerSubject")));
            audit.setToolName(payload.get("toolName"));
            audit.setArgumentsSummary(emptyToNull(payload.get("argumentsSummary")));
            audit.setSuccess(Boolean.parseBoolean(payload.getOrDefault("success", "false")));
            audit.setErrorMessage(emptyToNull(payload.get("errorMessage")));
            audit.setDurationMs(parseInt(payload.get("durationMs"), 0));
            repository.save(audit);
        } catch (Exception ex) {
            log.error("audit mq consume failed keys={}: {}", payload.get("keys"), ex.getMessage());
            throw ex;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
