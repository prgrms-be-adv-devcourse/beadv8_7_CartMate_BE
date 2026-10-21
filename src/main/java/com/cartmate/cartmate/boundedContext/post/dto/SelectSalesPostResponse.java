package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectSalesPostResponse {
    private Long id;
    private Long sellerId;
    private String title;
    private String content;
    private Long viewCount;
    private SalesPostSaleStatus status;
    private List<SelectSalesPostProduct> productList;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
