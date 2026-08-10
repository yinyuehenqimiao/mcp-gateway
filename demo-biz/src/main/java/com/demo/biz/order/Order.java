package com.demo.biz.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "订单")
public class Order {

    @Schema(description = "订单 ID", example = "o2001")
    private String id;

    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    @Schema(description = "商品 ID", example = "p1001")
    private String productId;

    @Schema(description = "购买数量", example = "2")
    private Integer quantity;

    @Schema(description = "订单金额", example = "598.00")
    private BigDecimal amount;

    @Schema(description = "状态：CREATED / PAID / CANCELLED", example = "PAID")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    public Order() {
    }

    public Order(
            String id,
            String userId,
            String productId,
            Integer quantity,
            BigDecimal amount,
            String status,
            LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
