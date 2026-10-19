package com.cartmate.cartmate.boundedContext.product.domain;


import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.SyncProductImage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "PRODUCT_PRODUCT_IMAGE",
        indexes = @Index(name = "idx_product_image_product", columnList = "product_id, image_seq")
)
@NoArgsConstructor
@Getter
public class ProductImage extends BaseIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private int imageSeq;


    public ProductImage(Long sellerId, Product product, String s3Key, int imageSeq) {
        this.sellerId = sellerId;
        this.product = product;
        this.s3Key = s3Key;
        this.imageSeq = imageSeq;
    }

    public SyncProductImage toSyncProductImage() {
        return new SyncProductImage(
                getId(),
                getSellerId(),
                getS3Key(),
                getImageSeq(),
                getCreateDate(),
                getModifyDate()
        );
    }
}
