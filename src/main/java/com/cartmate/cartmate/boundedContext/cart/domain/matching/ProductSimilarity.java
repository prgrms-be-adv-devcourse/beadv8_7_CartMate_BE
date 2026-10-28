package com.cartmate.cartmate.boundedContext.cart.domain.matching;

public record ProductSimilarity(
        Long productId,
        double similarity,
        MatchType matchType
) {

    public ProductSimilarity {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (matchType == null) {
            throw new IllegalArgumentException("matchType is required");
        }
        if (Double.isNaN(similarity) || similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException("similarity must be between 0 and 1");
        }
    }

    public static ProductSimilarity exact(Long productId) {
        return new ProductSimilarity(productId, 1.0, MatchType.EXACT);
    }

    public boolean isExact() {
        return matchType == MatchType.EXACT;
    }

    public boolean isStrongerThan(ProductSimilarity other) {
        if (similarity != other.similarity) {
            return similarity > other.similarity;
        }
        return isExact() && !other.isExact();
    }
}
