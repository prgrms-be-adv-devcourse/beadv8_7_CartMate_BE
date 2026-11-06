package com.cartmate.cartmate.boundedContext.productService.product.in.internal;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상품 서버 내부 API. 다른 서버(주문)만 부른다 — 외부 요청은 Gateway가 막고, 앱은 내부 API 키로 한 번 더 확인한다.
 * 프론트 API 문서·생성 코드에 나오지 않게 숨긴다.
 */
@Hidden
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductFacade productFacade;

    // 장바구니 담기 · 주문 생성에 필요한 구매용 상품 정보. 없는 상품은 결과에서 빠진다
    @GetMapping("/for-purchase")
    public RsData<List<ProductForPurchase>> findForPurchase(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "구매용 상품 조회", productFacade.findProductsForPurchase(ids));
    }
}
