package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.SyncProductOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PRODUCT_OPTION")
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

    public ProductOption(Product product, Long productTypeId, Long productOptionId) {
        this.product = product;
        this.productTypeId = productTypeId;
        this.productOptionId = productOptionId;
    }

    public SyncProductOption toSyncProductOption() {
        return new SyncProductOption(
                getId(),
                productTypeId,
                productOptionId,
                getCreateDate(),
                getModifyDate()
        );
    }
}
