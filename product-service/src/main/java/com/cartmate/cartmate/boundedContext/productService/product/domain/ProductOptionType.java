package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 옵션 종류 마스터 (예: 사이즈, 색상). 상품 옵션(ProductOption)과 옵션 값(ProductOptionValue)이 참조한다
@Entity
@Table(name = "PRODUCT_PRODUCT_OPTION_TYPE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionType extends BaseIdAndTime {

    @Column(nullable = false, length = 50)
    private String name;

    public ProductOptionType(String name) {
        this.name = name;
    }
}
