package com.mcp.gateway.service;

import com.mcp.gateway.domain.entity.ToolCallAudit;
import com.mcp.gateway.domain.repository.ToolCallAuditRepository;
import com.mcp.gateway.dto.AuditDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolCallAuditService {

    private final ToolCallAuditRepository repository;

    public ToolCallAuditService(ToolCallAuditRepository repository) {
        this.repository = repository;
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
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
