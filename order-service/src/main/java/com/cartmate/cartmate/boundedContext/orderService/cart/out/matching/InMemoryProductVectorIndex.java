package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
class InMemoryProductVectorIndex implements ProductVectorIndex {

    private final Map<Long, ProductVector> vectors = new ConcurrentHashMap<>();

    @Override
    public void saveAll(Collection<ProductVector> productVectors) {
        productVectors.forEach(vector -> vectors.put(vector.productId(), vector));
    }

    @Override
    public Optional<ProductVector> find(Long productId) {
        return Optional.ofNullable(vectors.get(productId));
    }

    @Override
    public List<ProductVector> findAll(Collection<Long> productIds) {
        return productIds.stream()
                .distinct()
                .map(vectors::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
