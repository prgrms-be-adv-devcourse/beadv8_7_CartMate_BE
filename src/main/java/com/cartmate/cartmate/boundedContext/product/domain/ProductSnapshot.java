package com.cartmate.cartmate.boundedContext.product.domain;

import java.util.List;

public record ProductSnapshot(
        String name,
        int price,
        String description,
        List<ImageSnapshot> images,
        List<OptionSnapshot> options
) {
    public record ImageSnapshot(Long imageId, String s3Key, int sortOrder) {}
    public record OptionSnapshot(Long optionId, String name, int additionalPrice) {}
}
