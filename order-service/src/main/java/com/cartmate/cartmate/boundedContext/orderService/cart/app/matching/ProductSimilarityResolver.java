package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductSimilarityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class ProductSimilarityResolver {

    private static final Comparator<ProductSimilarity> STRONGEST_FIRST =
            Comparator.comparingDouble(ProductSimilarity::similarity).reversed()
                    .thenComparing(ProductSimilarity::productId);

    private final List<ProductSimilarityFinder> finders;

    Map<Long, List<ProductSimilarity>> resolveAll(Collection<Long> sourceProductIds, Set<Long> candidateProductIds) {
        Set<Long> sources = Set.copyOf(sourceProductIds);
        Set<Long> candidates = Set.copyOf(candidateProductIds);
        if (sources.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, ProductSimilarity>> strongest = new HashMap<>();
        finders.forEach(finder -> findSafely(finder, sources, candidates).forEach((sourceProductId, similarities) -> {
            if (!sources.contains(sourceProductId)) {
                return;
            }
            Map<Long, ProductSimilarity> bySimilarProductId =
                    strongest.computeIfAbsent(sourceProductId, id -> new LinkedHashMap<>());
            similarities.stream()
                    .filter(similarity -> candidates.contains(similarity.productId()))
                    .forEach(similarity -> bySimilarProductId.merge(similarity.productId(), similarity,
                            (current, next) -> next.isStrongerThan(current) ? next : current));
        }));

        return strongest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().values().stream()
                        .sorted(STRONGEST_FIRST)
                        .toList()));
    }

    private static Map<Long, List<ProductSimilarity>> findSafely(ProductSimilarityFinder finder,
                                                                 Set<Long> sources, Set<Long> candidates) {
        try {
            return finder.findSimilar(sources, candidates);
        } catch (RuntimeException e) {
            log.warn("Product similarity finder {} failed; continuing without it", finder.getClass().getSimpleName(), e);
            return Map.of();
        }
    }
}
