package com.cartmate.cartmate.boundedContext.productService.post.dto.admin;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;

import java.time.LocalDateTime;

public record AdminSalesPostSummaryResponse(
        Long id,
        String title,
        Long sellerId,
        String sellerName,
        String thumbnailUrl,
        Long minPrice,
        int productCount,
        SalesPostSaleStatus status,
        Long viewCount,
        LocalDateTime createdAt
) {
}
