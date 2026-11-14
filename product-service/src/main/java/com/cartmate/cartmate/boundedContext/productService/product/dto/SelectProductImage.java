package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SelectProductImage {
    private Long id;
    private String s3key;
    private int imageSeq;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
