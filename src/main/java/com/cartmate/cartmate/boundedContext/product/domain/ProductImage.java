package com.cartmate.cartmate.boundedContext.product.domain;


import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "PRODUCT_IMAGE",
        indexes = @Index(name = "idx_product_image_product", columnList = "product_id, image_seq")
)
@NoArgsConstructor
@Getter
public class ProductImage extends BaseIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long productId;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private int imageSeq;


    public ProductImage(Long sellerId, Long productId, String s3Key, int imageSeq) {
        this.sellerId = sellerId;
        this.productId = productId;
        this.s3Key = s3Key;
        this.imageSeq = imageSeq;
    }
}
