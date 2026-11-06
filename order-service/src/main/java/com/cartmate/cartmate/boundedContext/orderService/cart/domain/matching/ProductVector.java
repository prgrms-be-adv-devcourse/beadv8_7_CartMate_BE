package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import java.util.Objects;

public record ProductVector(
        Long productId,
        Long categoryId,
        String model,
        String textVersion,
        float[] values
) {

    public ProductVector {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(textVersion, "textVersion is required");
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values are required");
        }
        values = values.clone();
    }

    public boolean isComparableTo(ProductVector other) {
        return model.equals(other.model)
                && textVersion.equals(other.textVersion)
                && values.length == other.values.length
                && Objects.equals(categoryId, other.categoryId);
    }

    public double cosine(ProductVector other) {
        double dot = 0;
        double left = 0;
        double right = 0;
        for (int i = 0; i < values.length; i++) {
            dot += values[i] * other.values[i];
            left += values[i] * values[i];
            right += other.values[i] * other.values[i];
        }
        if (left == 0 || right == 0) {
            return 0;
        }
        return dot / (Math.sqrt(left) * Math.sqrt(right));
    }
}
