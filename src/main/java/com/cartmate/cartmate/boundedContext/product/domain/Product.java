package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.code.ErrorCode;
import com.cartmate.cartmate.global.entity.BaseGeneratedInfo;
import com.cartmate.cartmate.global.exception.DomainException;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PRODUCT_PRODUCT")
@NoArgsConstructor
@Getter
public class Product extends BaseGeneratedInfo {
    private int sellerId;
    private int salesPostId;
    private int categoryId;
    private String name;
    private String description;
    private String basePrice;

    public Product(CreateProduct product) {
        this.sellerId = product.getSellerId();
        this.salesPostId = product.getSalesPostId();
    }

    public void publish(CreateProduct product) {
        if (this.sellerId != product.getSellerId()) {
            throw new DomainException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        this.categoryId = product.getCategoryId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.basePrice = product.getBasePrice();
    }
}
