package com.demo.biz.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建订单请求")
public class OrderRequest {

    @NotBlank
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    @NotBlank
    @Schema(description = "商品 ID", example = "p1001")
    private String productId;

    @NotNull
    @Min(1)
    @Schema(description = "购买数量", example = "1")
    private Integer quantity;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
