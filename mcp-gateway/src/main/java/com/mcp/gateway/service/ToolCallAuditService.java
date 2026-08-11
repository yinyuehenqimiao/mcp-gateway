package com.mcp.gateway.service;

import com.mcp.gateway.config.GatewayProperties;
import com.mcp.gateway.domain.entity.ToolCallAudit;
import com.mcp.gateway.domain.repository.ToolCallAuditRepository;
import com.mcp.gateway.dto.AuditDtos;
import com.mcp.gateway.service.audit.ToolCallAuditProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ToolCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditService.class);

    private final ToolCallAuditRepository repository;
    private final GatewayProperties gatewayProperties;
    private final ObjectProvider<ToolCallAuditProducer> auditProducer;

    public ToolCallAuditService(
            ToolCallAuditRepository repository,
            GatewayProperties gatewayProperties,
            ObjectProvider<ToolCallAuditProducer> auditProducer) {
        this.repository = repository;
        this.gatewayProperties = gatewayProperties;
        this.auditProducer = auditProducer;
    }

    /**
     * 热路径异步审计：优先发 RocketMQ；未启用或发送组件缺失时降级为同步写库失败也不抛给调用方。
     */
    public void recordAsync(
            String slug,
            String callerKeyHash,
            String callerSubject,
            String toolName,
            String argumentsSummary,
            boolean success,
            String errorMessage,
            int durationMs) {
        Map<String, String> payload = new HashMap<>();
        payload.put("keys", UUID.randomUUID().toString());
        payload.put("slug", nullToEmpty(slug));
        payload.put("callerKeyHash", nullToEmpty(callerKeyHash));
        payload.put("callerSubject", nullToEmpty(callerSubject));
        payload.put("toolName", nullToEmpty(toolName));
        payload.put("argumentsSummary", truncate(argumentsSummary, 1000));
        payload.put("success", String.valueOf(success));
        payload.put("errorMessage", truncate(errorMessage, 1000));
        payload.put("durationMs", String.valueOf(Math.max(durationMs, 0)));

        if (gatewayProperties.getAudit().getMq().isEnabled()) {
            ToolCallAuditProducer producer = auditProducer.getIfAvailable();
            if (producer != null) {
                producer.send(payload);
                return;
            }
            log.warn("audit mq enabled but producer missing, drop async audit tool={}", toolName);
            return;
        }
        try {
            record(
                    slug,
                    callerKeyHash,
                    callerSubject,
                    toolName,
                    argumentsSummary,
                    success,
                    errorMessage,
                    durationMs);
        } catch (Exception ex) {
            log.warn("fallback sync audit failed tool={}: {}", toolName, ex.getMessage());
        }
    }

    @Transactional
    public void record(
            String slug,
            String callerKeyHash,
            String callerSubject,
            String toolName,
            String argumentsSummary,
            boolean success,
            String errorMessage,
            int durationMs) {
        ToolCallAudit audit = new ToolCallAudit();
        audit.setSlug(slug);
        audit.setCallerKeyHash(callerKeyHash);
        audit.setCallerSubject(callerSubject);
        audit.setToolName(toolName);
        audit.setArgumentsSummary(truncate(argumentsSummary, 1000));
        audit.setSuccess(success);
        audit.setErrorMessage(truncate(errorMessage, 1000));
        audit.setDurationMs(Math.max(durationMs, 0));
        repository.save(audit);
    }

    @Transactional(readOnly = true)
    public AuditDtos.AuditPage search(String slug, String toolName, Boolean success, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<ToolCallAudit> result = repository.search(
                slug,
                toolName,
                success,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return new AuditDtos.AuditPage(
                result.getContent().stream().map(this::toItem).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private AuditDtos.AuditItem toItem(ToolCallAudit audit) {
        return new AuditDtos.AuditItem(
                audit.getId(),
                audit.getSlug(),
                audit.getCallerKeyHash(),
                audit.getCallerSubject(),
                audit.getToolName(),
                audit.getArgumentsSummary(),
                audit.getSuccess(),
                audit.getErrorMessage(),
                audit.getDurationMs(),
                audit.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
