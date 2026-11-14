package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductEmbedding;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class JpaProductVectorIndex implements ProductVectorIndex {

    private final ProductEmbeddingRepository repository;

    @Override
    @Transactional
    public void saveAll(Collection<ProductVector> vectors) {
        Map<Long, ProductVector> latest = vectors.stream()
                .collect(Collectors.toMap(ProductVector::productId, Function.identity(), (first, second) -> second,
                        LinkedHashMap::new));
        if (latest.isEmpty()) {
            return;
        }
        Map<Long, ProductEmbedding> existing = repository.findAllById(latest.keySet()).stream()
                .collect(Collectors.toMap(ProductEmbedding::getProductId, Function.identity()));
        List<ProductEmbedding> toSave = latest.values().stream()
                .map(vector -> {
                    ProductEmbedding embedding = existing.get(vector.productId());
                    if (embedding == null) {
                        return ProductEmbedding.of(vector);
                    }
                    embedding.replace(vector);
                    return embedding;
                })
                .toList();
        repository.saveAll(toSave);
    }

    @Override
    public Optional<ProductVector> find(Long productId) {
        return repository.findById(productId).flatMap(JpaProductVectorIndex::toProductVector);
    }

    @Override
    public List<ProductVector> findAll(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(new HashSet<>(productIds)).stream()
                .map(JpaProductVectorIndex::toProductVector)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Set<Long> findUpToDate(Collection<Long> productIds, String model, String textVersion) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findUpToDateProductIds(new HashSet<>(productIds), model, textVersion));
    }

    private static Optional<ProductVector> toProductVector(ProductEmbedding embedding) {
        try {
            return Optional.of(embedding.toProductVector());
        } catch (RuntimeException e) {
            log.warn("Skipping unreadable product embedding: productId={}", embedding.getProductId(), e);
            return Optional.empty();
        }
    }
}
