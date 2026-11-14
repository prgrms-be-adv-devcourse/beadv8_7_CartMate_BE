package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectSalesPostProduct {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private SalesPostProductStatus status;
    private List<SelectSalesPostProductImage> productImageList;
    private List<SelectSalesPostProductOption> productOptionList;
    private List<SelectSalesPostOptionGroup> optionGroups;   // 상세 화면 선택 목록 (목록 조회에서는 빈 목록)
    private List<SelectSalesPostInventory> inventoryList;     // 재고별 조합 · 가격 · 구매 가능 수량 (목록 조회에서는 빈 목록)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 상세 조회에서만 상품 서버의 선택 목록 · 재고를 붙인다
    public void attachInventories(List<SelectSalesPostOptionGroup> optionGroups, List<SelectSalesPostInventory> inventoryList) {
        this.optionGroups = optionGroups;
        this.inventoryList = inventoryList;
    }
}
