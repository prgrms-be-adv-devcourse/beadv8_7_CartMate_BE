package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CartDemandReader {

    List<ProductDemand> findRecentDemands(LocalDateTime since, int minMemberCount, int limit,
                                         Collection<Long> excludedProductIds);
}
