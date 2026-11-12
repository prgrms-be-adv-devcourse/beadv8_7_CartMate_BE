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

    @Column(nullable = false)
    private Long productTypeId;

    @Column(nullable = false)
    private Long productOptionId;

    // 옵션은 지우지 않고 상태로 남긴다(옵션 ID를 가리키는 SKU · 주문 이력이 끊기지 않게). 사진과 같은 ACTIVE / DELETED
    // 컬럼이 생기기 전에 있던 옵션은 ACTIVE로 채운다(ddl-auto: update)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    private ProductStatus status;

    public ProductOption(Product product, Long productTypeId, Long productOptionId, ProductStatus status) {
        this.product = product;
        this.productTypeId = productTypeId;
        this.productOptionId = productOptionId;
        this.status = status;
    }

    public SyncProductOption toSyncProductOption() {
        return new SyncProductOption(
                getId(),
                productTypeId,
                productOptionId,
                getCreateDate(),
                getModifyDate(),
                status
        );
    }

    public void update(Long productTypeId, Long productOptionId) {
        this.productTypeId = productTypeId;
        this.productOptionId = productOptionId;
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    public ProductOption delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }

    // 수정 실패 복구: 수정 때 지운 옵션을 같은 행(같은 ID)으로 되살린다
    public ProductOption restore() {
        this.status = ProductStatus.ACTIVE;
        return this;
    }
}
