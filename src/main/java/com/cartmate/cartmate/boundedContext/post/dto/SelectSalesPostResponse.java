package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.domain.SellerTrustLevel;
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
    private Integer sellerTrustScore;          // 판매자 신뢰도 점수. 점수가 없는 NEW 등급이면 null
    private SellerTrustLevel sellerTrustLevel; // 판매자 신뢰도 등급. 신뢰도 정보가 없는 판매자는 NEW
    private String title;
    private String content;
    private Long viewCount;
    private SalesPostSaleStatus status;
    private List<SelectSalesPostProduct> productList;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
