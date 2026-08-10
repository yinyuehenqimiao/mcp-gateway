package com.mcp.gateway.controller;

import com.mcp.gateway.service.BizSystemService;
import com.mcp.gateway.dto.SystemDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/systems")
public class BizSystemController {

    private final BizSystemService bizSystemService;

    public BizSystemController(BizSystemService bizSystemService) {
        this.bizSystemService = bizSystemService;
    }

    @PostMapping
    public SystemDtos.SystemResponse create(@Valid @RequestBody SystemDtos.CreateSystemRequest request) {
        return bizSystemService.create(request);
    }

    @GetMapping
    public List<SystemDtos.SystemResponse> list() {
        return bizSystemService.list();
    }

    @GetMapping("/{id}")
    public SystemDtos.SystemResponse get(@PathVariable Long id) {
        return bizSystemService.get(id);
    }

    @PutMapping("/{id}")
    public SystemDtos.SystemResponse update(
            @PathVariable Long id,
            @RequestBody SystemDtos.UpdateSystemRequest request) {
        return bizSystemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        bizSystemService.delete(id);
    }
}
