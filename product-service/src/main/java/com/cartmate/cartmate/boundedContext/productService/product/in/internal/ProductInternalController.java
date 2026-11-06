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
}
