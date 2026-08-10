package com.demo.biz.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "订单管理", description = "订单增删改查")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(operationId = "listOrders", summary = "查询订单列表", description = "可按用户、状态筛选")
    @GetMapping
    public List<Order> list(
            @Parameter(description = "用户 ID", example = "u1") @RequestParam(required = false) String userId,
            @Parameter(description = "状态：CREATED / PAID / CANCELLED") @RequestParam(required = false) String status) {
        return orderService.list(userId, status);
    }

    @Operation(operationId = "getOrderById", summary = "按 ID 查询订单")
    @GetMapping("/{id}")
    public Order get(@Parameter(description = "订单 ID", example = "o2001") @PathVariable String id) {
        return orderService.get(id);
    }

    @Operation(operationId = "createOrder", summary = "创建订单", description = "会校验商品是否上架并扣减库存")
    @PostMapping
    public Order create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    @Operation(operationId = "updateOrderStatus", summary = "更新订单状态")
    @PutMapping("/{id}/status")
    public Order updateStatus(
            @Parameter(description = "订单 ID") @PathVariable String id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request);
    }

    @Operation(operationId = "deleteOrder", summary = "删除订单")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "订单 ID") @PathVariable String id) {
        orderService.delete(id);
    }
}
