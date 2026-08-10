package com.mcp.gateway.common.exception;

/**
 * 业务可预期异常，映射为 4xx 响应。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this("BAD_REQUEST", message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = "BAD_REQUEST";
    }

    public String getCode() {
        return code;
    }
}
