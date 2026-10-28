package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class ExactProductSimilarityFinder implements ProductSimilarityFinder {

    @Override
    public Map<Long, List<ProductSimilarity>> findSimilar(Collection<Long> sourceProductIds, Set<Long> candidateProductIds) {
        return sourceProductIds.stream()
                .distinct()
                .filter(candidateProductIds::contains)
                .collect(Collectors.toMap(Function.identity(), productId -> List.of(ProductSimilarity.exact(productId))));
    }
}
