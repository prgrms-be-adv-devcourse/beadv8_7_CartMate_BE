package com.cartmate.cartmate.boundedContext.productService.product.in.internal;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
import com.cartmate.cartmate.shared.product.dto.InventoryReleaseRequest;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductFacade productFacade;

    @GetMapping("/for-purchase")
    public RsData<List<ProductForPurchase>> findForPurchase(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "구매용 상품 조회", productFacade.findProductsForPurchase(ids));
    }

    // 재고 ID로 조회: 장바구니 · 주문이 담은 재고(상품 + 선택된 옵션 조합)의 옵션명 · 적용가 · 구매 가능 여부
    @GetMapping("/inventories/for-purchase")
    public RsData<List<InventoryForPurchase>> findInventoriesForPurchase(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "구매용 재고 조회", productFacade.findInventoriesForPurchase(ids));
    }

    // 상품 ID로 조회: 그 상품의 살아 있는 재고 목록(공동구매 · 매칭의 옵션 선택)
    @GetMapping("/inventories/for-purchase/by-products")
    public RsData<List<InventoryForPurchase>> findInventoriesForPurchaseByProducts(@RequestParam List<Long> productIds) {
        return new RsData<>("200-1", "상품별 구매용 재고 조회", productFacade.findInventoriesForPurchaseByProducts(productIds));
    }

    // 재고 선점: 주문 결제 직전. 하나라도 부족하면 409(PRODUCT_INVENTORY_OUT_OF_STOCK)이고 아무것도 선점하지 않는다
    @PostMapping("/inventories/reserve")
    public RsData<Void> reserveInventories(@RequestBody InventoryReserveRequest request) {
        productFacade.reserveInventories(request);
        return new RsData<>("200-1", "재고 선점");
    }

    // 재고 선점 해제: 결제 실패. 풀 선점이 없으면 아무것도 하지 않는다
    @PostMapping("/inventories/release")
    public RsData<Void> releaseInventories(@RequestBody InventoryReleaseRequest request) {
        productFacade.releaseInventories(request);
        return new RsData<>("200-1", "재고 선점 해제");
    }
}
