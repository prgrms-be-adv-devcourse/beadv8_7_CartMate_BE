package com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductOptions;

import java.time.LocalDateTime;
import java.util.List;

public record CartMatchResponse(
        Long individualGroupPurchaseId,
        String title,
        String inviteCode,
        LocalDateTime deadlineAt,
        Integer paybackMinBuyers,
        Long paybackAmount,
        Long productId,
        String productName,
        String imageUrl,
        double similarity,
        MatchType matchType,
        List<Long> matchedCartProductIds
) {

    public static CartMatchResponse of(IndividualGroupPurchase purchase, CartProductOptions product,
                                       double similarity, MatchType matchType, List<Long> matchedCartProductIds) {
        return new CartMatchResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getInviteCode(),
                purchase.getDeadlineAt(),
                purchase.getPaybackMinBuyers(),
                purchase.getPaybackAmount(),
                purchase.getSourceProductId(),
                product == null ? null : product.productName(),
                product == null ? null : product.imageUrl(),
                similarity,
                matchType,
                List.copyOf(matchedCartProductIds)
        );
    }
}
