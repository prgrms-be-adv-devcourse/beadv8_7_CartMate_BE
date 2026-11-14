package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 옵션 값 마스터 (예: 사이즈 → L). 상품 옵션(ProductOption)이 productOptionId로 참조한다
@Entity
@Table(name = "PRODUCT_PRODUCT_OPTION_VALUE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionValue extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private ProductOptionType type;

    @Column(nullable = false, length = 100)
    private String name;

    // 추가 금액은 옵션 값이 아니라 재고(상품 + 옵션 조합) 단위로 둔다(Inventory 도입 예정)
    public ProductOptionValue(ProductOptionType type, String name) {
        if (type == null) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OPTION_VALUE_INVALID);
        }
        this.type = type;
        this.name = name;
    }
}
