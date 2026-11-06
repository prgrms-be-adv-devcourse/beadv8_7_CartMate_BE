package com.cartmate.cartmate.boundedContext.productService.post.domain;

import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostProductOption;
import com.cartmate.cartmate.shared.product.dto.SyncProductOption;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_PRODUCT_OPTION")
@NoArgsConstructor
@Getter
public class SalesPostProductOption extends BaseManualIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private SalesPostProduct salesPostProduct;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long productTypeId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long productOptionId;

    public SalesPostProductOption(Long id, SalesPostProduct salesPostProduct, Long productTypeId, Long productOptionId,
                                  LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.salesPostProduct = salesPostProduct;
        this.productTypeId = productTypeId;
        this.productOptionId = productOptionId;
    }

    public static SalesPostProductOption from(SyncProductOption option, SalesPostProduct salesPostProduct) {
        return new SalesPostProductOption(
                option.getId(),
                salesPostProduct,
                option.getProductTypeId(),
                option.getProductOptionId(),
                option.getCreateDate(),
                option.getModifyDate()
        );
    }

}