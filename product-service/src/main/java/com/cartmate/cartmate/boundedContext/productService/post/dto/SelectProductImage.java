package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
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
    private SalesPostProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
