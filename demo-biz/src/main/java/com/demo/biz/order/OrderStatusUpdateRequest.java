package com.demo.biz.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "更新订单状态请求")
public class OrderStatusUpdateRequest {

    @NotBlank
    @Schema(description = "目标状态：CREATED / PAID / CANCELLED", example = "PAID")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
