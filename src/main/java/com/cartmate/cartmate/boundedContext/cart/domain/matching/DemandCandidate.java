package com.cartmate.cartmate.boundedContext.cart.domain.matching;

import java.util.List;

public record DemandCandidate(
        Long productId,
        int exactMemberCount,
        int similarMemberCount,
        List<SimilarProductDemand> similarProducts
) {

    public DemandCandidate {
        similarProducts = List.copyOf(similarProducts);
    }

    public int totalMemberCount() {
        return exactMemberCount + similarMemberCount;
    }
}
