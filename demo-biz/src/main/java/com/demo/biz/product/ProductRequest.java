package com.demo.biz.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "创建/更新商品请求")
public class ProductRequest {

    @NotBlank
    @Schema(description = "商品名称", example = "无线鼠标")
    private String name;

    @NotBlank
    @Schema(description = "分类", example = "数码")
    private String category;

    @NotNull
    @DecimalMin("0.01")
    @Schema(description = "价格", example = "99.00")
    private BigDecimal price;

    @NotNull
    @Min(0)
    @Schema(description = "库存", example = "100")
    private Integer stock;

    @Schema(description = "是否上架", example = "true")
    private Boolean onSale = true;

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
