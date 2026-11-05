package com.cartmate.cartmate.boundedContext.productService.shared.product.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductImage {
    private Long id;
    private Long sellerId;
    private String s3Key;
    private int imageSeq;
    private ProductStatus status;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;

    public SalesPostProductImage getNewImage(SalesPostProduct newProduct) {
        return new SalesPostProductImage(
                this.getId(),
                this.getSellerId(),
                newProduct,
                this.getS3Key(),
                this.getImageSeq(),
                this.getStatus(),
                this.getCreateDate(),
                this.getModifyDate()
        );
    }
}
