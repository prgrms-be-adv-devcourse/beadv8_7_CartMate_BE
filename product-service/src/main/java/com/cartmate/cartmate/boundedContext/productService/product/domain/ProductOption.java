package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductOption;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "PRODUCT_PRODUCT_OPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 이 선택된 옵션이 속한 재고(상품옵션 조합). 재고 하나에 옵션 종류마다 하나씩 붙는다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    // 컬럼명은 그대로(product_type_id · product_option_id) 두고 마스터 테이블을 FK로 참조한다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductOptionType optionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOptionValue optionValue;

    // 옵션은 지우지 않고 상태로 남긴다(옵션 ID를 가리키는 SKU · 주문 이력이 끊기지 않게). 사진과 같은 ACTIVE / DELETED
    // 컬럼이 생기기 전에 있던 옵션은 ACTIVE로 채운다(ddl-auto: update)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    private ProductStatus status;

    // 재고를 만들 때(Inventory.create)만 만든다. 옵션 종류는 상품옵션이 가진 종류를 그대로 따른다
    ProductOption(Product product, Inventory inventory, ProductOptionValue optionValue, ProductStatus status) {
        this.product = product;
        this.inventory = inventory;
        this.optionType = optionValue.getType();
        this.optionValue = optionValue;
        this.status = status;
    }

    // 참조 ID. 프록시에서 ID만 꺼내므로 마스터 테이블을 조회하지 않는다
    public Long getProductTypeId() {
        return optionType.getId();
    }

    public Long getProductOptionId() {
        return optionValue.getId();
    }

    public SyncProductOption toSyncProductOption() {
        return new SyncProductOption(
                getId(),
                getProductTypeId(),
                getProductOptionId(),
                getCreateDate(),
                getModifyDate(),
                status
        );
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    public ProductOption delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }

    // 수정 실패 복구: 재고와 함께 지운 옵션을 같은 행(같은 ID)으로 되살린다
    public ProductOption restore() {
        this.status = ProductStatus.ACTIVE;
        return this;
    }
}
