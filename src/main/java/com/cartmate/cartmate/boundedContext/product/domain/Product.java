package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.code.ErrorCode;

import com.cartmate.cartmate.global.exception.DomainException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "PRODUCT_PRODUCT")
@NoArgsConstructor
@Getter
public class Product extends BaseIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long salesPostId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long basePrice;

    public Product(CreateProduct product) {
        this.sellerId = product.getSellerId();
        this.salesPostId = product.getSalesPostId();
    }

    public void publish(CreateProduct product) {
        if (!Objects.equals(this.sellerId, product.getSellerId())) {
            throw new DomainException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        this.categoryId = product.getCategoryId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.basePrice = product.getBasePrice();
    }
}
