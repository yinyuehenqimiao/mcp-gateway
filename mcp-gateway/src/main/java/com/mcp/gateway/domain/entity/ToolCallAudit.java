package com.mcp.gateway.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** MCP 工具调用审计实体，对应 tool_call_audit 表。 */
@Entity
@Table(name = "tool_call_audit")
public class ToolCallAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(name = "caller_key_hash", length = 64)
    private String callerKeyHash;

    @Column(name = "caller_subject", length = 128)
    private String callerSubject;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "arguments_summary", length = 1024)
    private String argumentsSummary;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCallerKeyHash() {
        return callerKeyHash;
    }

    public void setCallerKeyHash(String callerKeyHash) {
        this.callerKeyHash = callerKeyHash;
    }

    public String getCallerSubject() {
        return callerSubject;
    }

    public void setCallerSubject(String callerSubject) {
        this.callerSubject = callerSubject;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgumentsSummary() {
        return argumentsSummary;
    }

    public void setArgumentsSummary(String argumentsSummary) {
        this.argumentsSummary = argumentsSummary;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
