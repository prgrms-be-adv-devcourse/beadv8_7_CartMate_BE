package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 판매글 수정 끝: 수정 중(UPDATING)인 상품을 수정 전 판매 상태로 되돌린다. post가 스냅샷의 상태를 담아 보낸다
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProductUpdateRequest {
    private List<Item> productList;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private SalesPostProductStatus status;   // 수정 전 판매 상태
    }
}
