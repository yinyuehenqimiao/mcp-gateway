package com.demo.biz.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "获取 JWT，供业务 API 与 MCP Gateway API Key 联调")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(JwtService jwtService, JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Operation(operationId = "login", summary = "登录获取 JWT")
    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        if (!jwtProperties.getDemoUsername().equals(request.username())
                || !jwtProperties.getDemoPassword().equals(request.password())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtService.issueToken(request.username());
        return new AuthDtos.LoginResponse(token, "Bearer", jwtService.expireSeconds(), request.username());
    }
}
