package com.cartmate.cartmate.boundedContext.post.dto.admin;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSanctionAction;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSalesPostDetailResponse(
        Long id,
        String title,
        String content,
        Long sellerId,
        String sellerName,
        SalesPostSaleStatus status,
        Long viewCount,
        LocalDateTime createdAt,
        List<AdminSalesPostProduct> products,
        List<AdminSalesPostSanction> sanctions
) {
    public record AdminSalesPostProduct(Long id, String name, Long basePrice, int optionCount, List<String> imageUrls) {}

    public record AdminSalesPostSanction(Long id, Long adminId, SalesPostSanctionAction action, String reason, LocalDateTime createdAt) {}
}
