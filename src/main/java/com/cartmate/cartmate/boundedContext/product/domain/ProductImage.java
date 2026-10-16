package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.entity.BaseGeneratedInfo;
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
public class ProductImage extends BaseGeneratedInfo {
    private int sellerId;
    private int productId;
    private String s3Key;
    private int imageSeq;

    public ProductImage(int sellerId, int productId, String s3Key, int imageSeq) {
        this.sellerId = sellerId;
        this.productId = productId;
        this.s3Key = s3Key;
        this.imageSeq = imageSeq;
    }
}
