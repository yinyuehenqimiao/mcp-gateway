package com.mcp.gateway.service;

import com.mcp.gateway.common.exception.BusinessException;

import com.mcp.gateway.domain.entity.BizSystem;
import com.mcp.gateway.domain.repository.BizSystemRepository;
import com.mcp.gateway.dto.SystemDtos;
import com.mcp.gateway.service.tool.DynamicToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BizSystemService {

    private final BizSystemRepository bizSystemRepository;
    private final DynamicToolRegistry dynamicToolRegistry;

    public BizSystemService(
            BizSystemRepository bizSystemRepository,
            DynamicToolRegistry dynamicToolRegistry) {
        this.bizSystemRepository = bizSystemRepository;
        this.dynamicToolRegistry = dynamicToolRegistry;
    }

    @Transactional
    public SystemDtos.SystemResponse create(SystemDtos.CreateSystemRequest request) {
        if (bizSystemRepository.existsByCode(request.code())) {
            throw new BusinessException("系统 code 已存在: " + request.code());
        }
        BizSystem system = new BizSystem();
        system.setName(request.name());
        system.setCode(request.code());
        system.setBaseUrl(trimSlash(request.baseUrl()));
        system.setDescription(request.description());
        system.setAuthType(request.authType() == null || request.authType().isBlank() ? "NONE" : request.authType());
        system.setAuthConfig(request.authConfig());
        return toResponse(bizSystemRepository.save(system));
    }

    @Transactional(readOnly = true)
    public List<SystemDtos.SystemResponse> list() {
        return bizSystemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SystemDtos.SystemResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public SystemDtos.SystemResponse update(Long id, SystemDtos.UpdateSystemRequest request) {
        BizSystem system = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            system.setName(request.name());
        }
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            system.setBaseUrl(trimSlash(request.baseUrl()));
        }
        if (request.description() != null) {
            system.setDescription(request.description());
        }
        if (request.authType() != null && !request.authType().isBlank()) {
            system.setAuthType(request.authType());
        }
        if (request.authConfig() != null) {
            system.setAuthConfig(request.authConfig());
        }
        SystemDtos.SystemResponse response = toResponse(bizSystemRepository.saveAndFlush(system));
        // baseUrl / 鉴权变更后必须刷新已发布工具映射，否则会继续打到旧地址（如 8001→404）
        dynamicToolRegistry.refresh();
        return response;
    }

    @Transactional
    public void delete(Long id) {
        bizSystemRepository.delete(require(id));
        dynamicToolRegistry.refresh();
    }

    public BizSystem require(Long id) {
        return bizSystemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("业务系统不存在: " + id));
    }

    private SystemDtos.SystemResponse toResponse(BizSystem system) {
        return new SystemDtos.SystemResponse(
                system.getId(),
                system.getName(),
                system.getCode(),
                system.getBaseUrl(),
                system.getDescription(),
                system.getAuthType(),
                system.getAuthConfig()
        );
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
