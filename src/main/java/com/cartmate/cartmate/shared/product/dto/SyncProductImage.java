package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
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
}
