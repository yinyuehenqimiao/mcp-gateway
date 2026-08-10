package com.demo.biz.product;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final Map<String, Product> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1000);

    @PostConstruct
    public void initFakeData() {
        saveSeed(new Product("p1001", "机械键盘", "数码", new BigDecimal("299.00"), 50, true));
        saveSeed(new Product("p1002", "显示器 27寸", "数码", new BigDecimal("1299.00"), 20, true));
        saveSeed(new Product("p1003", "办公椅", "家具", new BigDecimal("599.00"), 15, true));
        saveSeed(new Product("p1004", "保温杯", "日用", new BigDecimal("69.00"), 200, false));
        seq.set(1004);
    }

    public List<Product> list(String category, Boolean onSale) {
        return store.values().stream()
                .filter(p -> category == null || category.isBlank() || category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> onSale == null || onSale.equals(p.getOnSale()))
                .sorted(Comparator.comparing(Product::getId))
                .collect(Collectors.toList());
    }

    public Product get(String id) {
        Product product = store.get(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在: " + id);
        }
        return product;
    }

    public Product create(ProductRequest request) {
        String id = "p" + seq.incrementAndGet();
        Product product = new Product(
                id,
                request.getName(),
                request.getCategory(),
                request.getPrice(),
                request.getStock(),
                request.getOnSale() == null || request.getOnSale()
        );
        store.put(id, product);
        return product;
    }

    public Product update(String id, ProductRequest request) {
        Product existing = get(id);
        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setPrice(request.getPrice());
        existing.setStock(request.getStock());
        if (request.getOnSale() != null) {
            existing.setOnSale(request.getOnSale());
        }
        store.put(id, existing);
        return existing;
    }

    public void delete(String id) {
        if (store.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在: " + id);
        }
    }

    public List<Product> all() {
        return new ArrayList<>(store.values());
    }

    private void saveSeed(Product product) {
        store.put(product.getId(), product);
    }
}
