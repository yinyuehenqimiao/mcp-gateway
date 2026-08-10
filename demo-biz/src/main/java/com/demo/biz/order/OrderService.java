package com.demo.biz.order;

import com.demo.biz.product.Product;
import com.demo.biz.product.ProductService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Set<String> ALLOWED_STATUS = Set.of("CREATED", "PAID", "CANCELLED");

    private final ProductService productService;
    private final Map<String, Order> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(2000);

    public OrderService(ProductService productService) {
        this.productService = productService;
    }

    @PostConstruct
    public void initFakeData() {
        store.put("o2001", new Order(
                "o2001", "u1", "p1001", 2,
                new BigDecimal("598.00"), "PAID",
                LocalDateTime.now().minusDays(2)));
        store.put("o2002", new Order(
                "o2002", "u1", "p1002", 1,
                new BigDecimal("1299.00"), "CREATED",
                LocalDateTime.now().minusHours(5)));
        store.put("o2003", new Order(
                "o2003", "u2", "p1003", 1,
                new BigDecimal("599.00"), "CANCELLED",
                LocalDateTime.now().minusDays(1)));
        seq.set(2003);
    }

    public List<Order> list(String userId, String status) {
        return store.values().stream()
                .filter(o -> userId == null || userId.isBlank() || userId.equals(o.getUserId()))
                .filter(o -> status == null || status.isBlank() || status.equalsIgnoreCase(o.getStatus()))
                .sorted(Comparator.comparing(Order::getId))
                .collect(Collectors.toList());
    }

    public Order get(String id) {
        Order order = store.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + id);
        }
        return order;
    }

    public Order create(OrderRequest request) {
        Product product = productService.get(request.getProductId());
        if (Boolean.FALSE.equals(product.getOnSale())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品未上架: " + product.getId());
        }
        if (product.getStock() < request.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足");
        }

        product.setStock(product.getStock() - request.getQuantity());
        String id = "o" + seq.incrementAndGet();
        BigDecimal amount = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        Order order = new Order(
                id,
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                amount,
                "CREATED",
                LocalDateTime.now()
        );
        store.put(id, order);
        return order;
    }

    public Order updateStatus(String id, OrderStatusUpdateRequest request) {
        String status = request.getStatus().toUpperCase();
        if (!ALLOWED_STATUS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法状态: " + request.getStatus());
        }
        Order order = get(id);
        order.setStatus(status);
        store.put(id, order);
        return order;
    }

    public void delete(String id) {
        if (store.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + id);
        }
    }
}
