package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상품 카테고리 마스터 (예: 유니폼 / 의류). 상품(Product)이 참조한다. 초기 데이터는 data.sql
@Entity
@Table(name = "PRODUCT_CATEGORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseIdAndTime {

    @Column(nullable = false, length = 50)
    private String name;

    public Category(String name) {
        this.name = name;
    }
}
