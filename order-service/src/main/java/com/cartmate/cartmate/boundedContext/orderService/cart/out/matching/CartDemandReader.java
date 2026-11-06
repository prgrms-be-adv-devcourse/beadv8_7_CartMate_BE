package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductDemand;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CartDemandReader {

    List<ProductDemand> findRecentDemands(LocalDateTime since, int minMemberCount, int limit,
                                         Collection<Long> excludedProductIds);

    List<Long> findRecentProductIdsByDemand(LocalDateTime since, int limit);
}
