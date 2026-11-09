package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProductSimilarityFinder {

    Map<Long, List<ProductSimilarity>> findSimilar(Collection<Long> sourceProductIds, Set<Long> candidateProductIds);
}
