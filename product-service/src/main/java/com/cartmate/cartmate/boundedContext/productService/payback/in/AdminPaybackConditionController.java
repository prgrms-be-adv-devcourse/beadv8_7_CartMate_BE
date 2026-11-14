package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products/{productId}/payback-conditions/admin")
@RequiredArgsConstructor
public class AdminPaybackConditionController {

    private final PaybackFacade paybackFacade;

    @PutMapping
    @Operation(operationId = "setAdminPaybackCondition", summary = "어드민 페이백 조건 설정")
    public RsData<PaybackConditionResponse> set(@PathVariable Long productId,
                                                @Valid @RequestBody AdminPaybackConditionRequest request) {
        return new RsData<>("200-1", "페이백 조건을 설정했습니다.", paybackFacade.setAdminCondition(productId, request));
    }

    @DeleteMapping
    @Operation(operationId = "releaseAdminPaybackCondition", summary = "어드민 페이백 조건 해제")
    public RsData<Void> release(@PathVariable Long productId) {
        paybackFacade.releaseAdminCondition(productId);
        return new RsData<>("200-1", "페이백 조건을 해제했습니다.");
    }
}
