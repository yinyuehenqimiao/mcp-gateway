package com.mcp.gateway.controller;

import com.mcp.gateway.dto.AuditDtos;
import com.mcp.gateway.service.ToolCallAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audits")
public class ToolCallAuditController {

    private final ToolCallAuditService toolCallAuditService;

    public ToolCallAuditController(ToolCallAuditService toolCallAuditService) {
        this.toolCallAuditService = toolCallAuditService;
    }

    @GetMapping
    public AuditDtos.AuditPage list(
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return toolCallAuditService.search(slug, toolName, success, page, size);
    }
}
