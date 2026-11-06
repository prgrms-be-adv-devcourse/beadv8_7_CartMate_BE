package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductVectorIndex {

    void saveAll(Collection<ProductVector> vectors);

    Optional<ProductVector> find(Long productId);

    List<ProductVector> findAll(Collection<Long> productIds);

    Set<Long> findUpToDate(Collection<Long> productIds, String model, String textVersion);
}
