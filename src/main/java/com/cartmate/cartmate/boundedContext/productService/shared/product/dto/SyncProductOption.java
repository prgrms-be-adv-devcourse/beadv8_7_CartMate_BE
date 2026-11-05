package com.cartmate.cartmate.boundedContext.productService.shared.product.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import lombok.AllArgsConstructor;
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

    public SalesPostProductOption getNewOption(SalesPostProduct newProduct) {
        return new SalesPostProductOption(
                this.getId(),
                newProduct,
                this.getProductTypeId(),
                this.getProductOptionId(),
                this.getCreateDate(),
                this.getModifyDate()
        );
    }
}
