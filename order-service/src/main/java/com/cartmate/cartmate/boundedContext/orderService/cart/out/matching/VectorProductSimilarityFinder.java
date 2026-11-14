package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "custom.matching.vector.enabled", havingValue = "true", matchIfMissing = true)
class VectorProductSimilarityFinder implements ProductSimilarityFinder {

    private final ProductVectorIndex productVectorIndex;
    private final double threshold;

    VectorProductSimilarityFinder(ProductVectorIndex productVectorIndex,
                                  @Value("${custom.matching.vector.threshold:0.90}") double threshold) {
        this.productVectorIndex = productVectorIndex;
        this.threshold = threshold;
    }

    @Override
    public Map<Long, List<ProductSimilarity>> findSimilar(Collection<Long> sourceProductIds, Set<Long> candidateProductIds) {
        Set<Long> wanted = new HashSet<>(candidateProductIds);
        wanted.addAll(sourceProductIds);
        Map<Long, ProductVector> loaded = productVectorIndex.findAll(wanted).stream()
                .collect(Collectors.toMap(ProductVector::productId, Function.identity(), (first, second) -> first));
        Map<Long, List<ProductVector>> candidatesByCategory = candidateProductIds.stream()
                .map(loaded::get)
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.categoryId() != null)
                .collect(Collectors.groupingBy(ProductVector::categoryId));
        Map<Long, List<ProductSimilarity>> result = new HashMap<>();
        for (ProductVector source : sourceProductIds.stream().distinct().map(loaded::get).filter(Objects::nonNull).toList()) {
            List<ProductSimilarity> similar = new ArrayList<>();
            for (ProductVector candidate : candidatesByCategory.getOrDefault(source.categoryId(), List.of())) {
                if (Objects.equals(candidate.productId(), source.productId()) || !source.isComparableTo(candidate)) {
                    continue;
                }
                double cosine = source.cosine(candidate);
                if (cosine >= threshold) {
                    similar.add(new ProductSimilarity(candidate.productId(), Math.min(1.0, cosine), MatchType.VECTOR));
                }
            }
            if (!similar.isEmpty()) {
                result.put(source.productId(), similar);
            }
        }
        return result;
    }
}
