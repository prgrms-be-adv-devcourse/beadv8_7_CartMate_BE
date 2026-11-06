package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectSalesPostProductOption {
    private Long id;
    private Long productTypeId;
    private Long productOptionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
