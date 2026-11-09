package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import com.cartmate.cartmate.shared.product.ProductStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductOption {
    private Long id;
    private Long productTypeId;
    private Long productOptionId;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
    private ProductStatus status; // ACTIVE / DELETED (옵션은 논리 삭제)
}
