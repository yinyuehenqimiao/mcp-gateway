package com.demo.biz.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "商品")
public class Product {

    @Schema(description = "商品 ID", example = "p1001")
    private String id;

    @Schema(description = "商品名称", example = "机械键盘")
    private String name;

    @Schema(description = "分类", example = "数码")
    private String category;

    @Schema(description = "价格", example = "299.00")
    private BigDecimal price;

    @Schema(description = "库存", example = "50")
    private Integer stock;

    @Schema(description = "是否上架", example = "true")
    private Boolean onSale;

    public Product() {
    }

    public Product(String id, String name, String category, BigDecimal price, Integer stock, Boolean onSale) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.onSale = onSale;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getOnSale() {
        return onSale;
    }

    public void setOnSale(Boolean onSale) {
        this.onSale = onSale;
    }
}
