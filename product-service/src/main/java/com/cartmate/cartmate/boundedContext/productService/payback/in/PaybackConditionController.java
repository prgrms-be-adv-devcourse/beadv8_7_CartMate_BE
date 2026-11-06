package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/payback-conditions")
@RequiredArgsConstructor
public class PaybackConditionController {

    private final PaybackFacade paybackFacade;

    @GetMapping
    @Operation(operationId = "getProductPaybackConditions", summary = "상품 페이백 조건 조회")
    public RsData<List<PaybackConditionResponse>> getConditions(@PathVariable Long productId) {
        return new RsData<>("200-1", "페이백 조건을 조회했습니다.", paybackFacade.getConditions(productId));
    }

    @PutMapping("/seller")
    @Operation(operationId = "setSellerPaybackCondition", summary = "판매자 페이백 조건 설정")
    public RsData<PaybackConditionResponse> setSellerCondition(@AuthenticationPrincipal SecurityUser user,
                                                               @PathVariable Long productId,
                                                               @Valid @RequestBody SellerPaybackConditionRequest request) {
        return new RsData<>("200-1", "페이백 조건을 설정했습니다.",
                paybackFacade.setSellerCondition(sellerIdOf(user), productId, request));
    }

    @DeleteMapping("/seller")
    @Operation(operationId = "releaseSellerPaybackCondition", summary = "판매자 페이백 조건 해제")
    public RsData<Void> releaseSellerCondition(@AuthenticationPrincipal SecurityUser user,
                                               @PathVariable Long productId) {
        paybackFacade.releaseSellerCondition(sellerIdOf(user), productId);
        return new RsData<>("200-1", "페이백 조건을 해제했습니다.");
    }

    private static Long sellerIdOf(SecurityUser user) {
        return user == null ? null : user.sellerId();
    }
}
