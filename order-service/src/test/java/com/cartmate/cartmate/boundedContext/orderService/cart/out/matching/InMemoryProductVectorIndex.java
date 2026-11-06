package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

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

    @Override
    public Set<Long> findUpToDate(Collection<Long> productIds, String model, String textVersion) {
        return productIds.stream()
                .map(vectors::get)
                .filter(Objects::nonNull)
                .filter(vector -> vector.model().equals(model) && vector.textVersion().equals(textVersion))
                .map(ProductVector::productId)
                .collect(Collectors.toSet());
    }
}
