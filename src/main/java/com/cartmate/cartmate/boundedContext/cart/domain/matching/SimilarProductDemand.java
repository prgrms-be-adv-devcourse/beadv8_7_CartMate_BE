package com.cartmate.cartmate.boundedContext.cart.domain.matching;

public record SimilarProductDemand(
        Long productId,
        double similarity,
        MatchType matchType,
        int memberCount
) {
}
