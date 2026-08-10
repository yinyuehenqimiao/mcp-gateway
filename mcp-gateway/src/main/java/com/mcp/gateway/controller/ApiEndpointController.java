package com.mcp.gateway.controller;

import com.mcp.gateway.service.ApiEndpointService;
import com.mcp.gateway.dto.ApiDtos;
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
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;

    public ApiEndpointController(ApiEndpointService apiEndpointService) {
        this.apiEndpointService = apiEndpointService;
    }

    @GetMapping("/systems/{systemId}/apis")
    public List<ApiDtos.ApiResponse> list(@PathVariable Long systemId) {
        return apiEndpointService.listBySystem(systemId);
    }

    @PostMapping("/systems/{systemId}/apis")
    public ApiDtos.ApiResponse createManual(
            @PathVariable Long systemId,
            @Valid @RequestBody ApiDtos.CreateApiRequest request) {
        return apiEndpointService.createManual(systemId, request);
    }

    @PostMapping("/systems/{systemId}/import/openapi")
    public ApiDtos.ImportResult importOpenApi(
            @PathVariable Long systemId,
            @RequestBody ApiDtos.ImportOpenApiRequest request) {
        return apiEndpointService.importOpenApi(systemId, request);
    }

    @GetMapping("/apis/{id}")
    public ApiDtos.ApiResponse get(@PathVariable Long id) {
        return apiEndpointService.get(id);
    }

    @PutMapping("/apis/{id}")
    public ApiDtos.ApiResponse update(
            @PathVariable Long id,
            @RequestBody ApiDtos.UpdateApiRequest request) {
        return apiEndpointService.update(id, request);
    }

    @DeleteMapping("/apis/{id}")
    public void delete(@PathVariable Long id) {
        apiEndpointService.delete(id);
    }

    @PostMapping("/apis/test-call")
    public Map<String, Object> testCall(@Valid @RequestBody ApiDtos.TestCallRequest request) {
        String result = apiEndpointService.testCall(request);
        return Map.of("result", result);
    }
}
