package com.cartmate.cartmate.boundedContext.post.domain;


import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "POST_PRODUCT_IMAGE",
        indexes = @Index(name = "idx_product_image_product", columnList = "product_id, image_seq")
)
@NoArgsConstructor
@Getter
public class SalesPostProductImage extends BaseManualIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private SalesPostProduct salesPostProduct;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private int imageSeq;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;


    public SalesPostProductImage(Long id, Long sellerId, SalesPostProduct salesPostProduct, String s3Key,
                                 int imageSeq, ProductStatus status, LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.sellerId = sellerId;
        this.salesPostProduct = salesPostProduct;
        this.s3Key = s3Key;
        this.imageSeq = imageSeq;
        this.status = status;
    }

    public SelectSalesPostProductImage toSelectProductImageDto() {
        return new SelectSalesPostProductImage(
                getId(),
                s3Key,
                imageSeq,
                getCreateDate(),
                getModifyDate()
        );
    }

    public SalesPostProductImage delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }
}
