package com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchasePaybackCondition;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductOptions;
import com.cartmate.cartmate.shared.cart.PaybackFunder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public record CartMatchResponse(
        Long individualGroupPurchaseId,
        String title,
        String inviteCode,
        LocalDateTime deadlineAt,
        Integer paybackMinQuantity,
        Long paybackAmountPerUnit,
        Long productId,
        String productName,
        String imageUrl,
        double similarity,
        MatchType matchType,
        List<Long> matchedCartProductIds
) {

    public static CartMatchResponse of(IndividualGroupPurchase purchase, CartProductOptions product,
                                       double similarity, MatchType matchType, List<Long> matchedCartProductIds) {
        Optional<IndividualGroupPurchasePaybackCondition> adminCondition =
                purchase.findPaybackCondition(purchase.getSourceProductId(), PaybackFunder.ADMIN);
        return new CartMatchResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getInviteCode(),
                purchase.getDeadlineAt(),
                adminCondition.map(IndividualGroupPurchasePaybackCondition::getMinQuantity).orElse(null),
                adminCondition.map(IndividualGroupPurchasePaybackCondition::getAmountPerUnit).orElse(null),
                purchase.getSourceProductId(),
                product == null ? null : product.productName(),
                product == null ? null : product.imageUrl(),
                similarity,
                matchType,
                List.copyOf(matchedCartProductIds)
        );
    }
}
