package com.cartmate.cartmate.boundedContext.productService.payback.dto;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackCondition;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;

public record PaybackConditionResponse(
        Long productId,
        PaybackFunder funder,
        int minQuantity,
        long amountPerUnit,
        PaybackOverlapPolicy overlapPolicy
) {

    public static PaybackConditionResponse from(PaybackCondition condition) {
        return new PaybackConditionResponse(condition.getProductId(), condition.getFunder(),
                condition.getMinQuantity(), condition.getAmountPerUnit(), condition.getOverlapPolicy());
    }
}
