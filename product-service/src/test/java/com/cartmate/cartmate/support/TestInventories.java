package com.cartmate.cartmate.support;

import java.util.List;

/**
 * 테스트용 재고 요청. 상품옵션 조합 하나로 재고 하나를 만든다(추가금액 0원, 재고 10개).
 * post 요청(판매글)과 product 요청(상품) DTO가 따로 있어 같은 이름으로 둘 다 받는다.
 */
public final class TestInventories {

    public static final long ADDITIONAL_PRICE = 0L;
    public static final int STOCK_QUANTITY = 10;

    public static com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory inventory(
            com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption... options) {
        return new com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory(
                List.of(options), ADDITIONAL_PRICE, STOCK_QUANTITY);
    }

    public static com.cartmate.cartmate.boundedContext.productService.post.dto.CreateInventory inventory(
            com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption... options) {
        return new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateInventory(
                List.of(options), ADDITIONAL_PRICE, STOCK_QUANTITY);
    }

    private TestInventories() {
    }
}
