package com.demo.biz.product;

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

@Tag(name = "商品管理", description = "商品增删改查")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(operationId = "listProducts", summary = "查询商品列表", description = "可按分类、是否上架筛选")
    @GetMapping
    public List<Product> list(
            @Parameter(description = "分类，如 数码/家具/日用") @RequestParam(required = false) String category,
            @Parameter(description = "是否上架") @RequestParam(required = false) Boolean onSale) {
        return productService.list(category, onSale);
    }

    @Operation(operationId = "getProductById", summary = "按 ID 查询商品")
    @GetMapping("/{id}")
    public Product get(@Parameter(description = "商品 ID", example = "p1001") @PathVariable String id) {
        return productService.get(id);
    }

    @Operation(operationId = "createProduct", summary = "创建商品")
    @PostMapping
    public Product create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @Operation(operationId = "updateProduct", summary = "更新商品")
    @PutMapping("/{id}")
    public Product update(
            @Parameter(description = "商品 ID") @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @Operation(operationId = "deleteProduct", summary = "删除商品")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "商品 ID") @PathVariable String id) {
        productService.delete(id);
    }
}
