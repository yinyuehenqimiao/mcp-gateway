package com.mcp.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditItem(
            Long id,
            String slug,
            String callerKeyHash,
            String callerSubject,
            String toolName,
            String argumentsSummary,
            Boolean success,
            String errorMessage,
            Integer durationMs,
            LocalDateTime createdAt
    ) {
    }

    public record AuditPage(
            List<AuditItem> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }
}
