package com.mcp.gateway.service;

import com.mcp.gateway.domain.entity.ToolCallAudit;
import com.mcp.gateway.domain.repository.ToolCallAuditRepository;
import com.mcp.gateway.dto.AuditDtos;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ToolCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditService.class);
    private static final int QUEUE_CAPACITY = 10_000;

    private final ToolCallAuditRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final LinkedBlockingQueue<AuditEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final ExecutorService worker;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong dropped = new AtomicLong();

    public ToolCallAuditService(ToolCallAuditRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(txManager);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "tool-audit-writer");
            t.setDaemon(true);
            return t;
        };
        this.worker = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                tf,
                new ThreadPoolExecutor.DiscardPolicy());
        this.worker.execute(this::drainLoop);
    }

    /**
     * 热路径非阻塞入队；队列满时丢弃并计数，避免拖垮 tools/call。
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
        AuditEvent event = new AuditEvent(
                slug,
                callerKeyHash,
                callerSubject,
                toolName,
                truncate(argumentsSummary, 1000),
                success,
                truncate(errorMessage, 1000),
                Math.max(durationMs, 0));
        if (!queue.offer(event)) {
            long n = dropped.incrementAndGet();
            if (n == 1L || n % 1000 == 0) {
                log.warn("审计队列已满，已丢弃 {} 条记录", n);
            }
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
        persist(new AuditEvent(
                slug,
                callerKeyHash,
                callerSubject,
                toolName,
                truncate(argumentsSummary, 1000),
                success,
                truncate(errorMessage, 1000),
                Math.max(durationMs, 0)));
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

    private void drainLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                AuditEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                if (event != null) {
                    transactionTemplate.executeWithoutResult(status -> persist(event));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("异步写入审计失败: {}", ex.getMessage());
            }
        }
    }

    private void persist(AuditEvent event) {
        ToolCallAudit audit = new ToolCallAudit();
        audit.setSlug(event.slug());
        audit.setCallerKeyHash(event.callerKeyHash());
        audit.setCallerSubject(event.callerSubject());
        audit.setToolName(event.toolName());
        audit.setArgumentsSummary(event.argumentsSummary());
        audit.setSuccess(event.success());
        audit.setErrorMessage(event.errorMessage());
        audit.setDurationMs(event.durationMs());
        repository.save(audit);
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

    @PreDestroy
    public void shutdown() {
        running.set(false);
        worker.shutdown();
        try {
            if (!worker.awaitTermination(3, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException | RejectedExecutionException ex) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record AuditEvent(
            String slug,
            String callerKeyHash,
            String callerSubject,
            String toolName,
            String argumentsSummary,
            boolean success,
            String errorMessage,
            int durationMs) {
    }
}
