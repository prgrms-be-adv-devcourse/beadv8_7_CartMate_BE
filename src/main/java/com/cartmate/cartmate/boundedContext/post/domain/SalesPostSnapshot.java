package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.post.dto.SelectProductImage;
import com.cartmate.cartmate.boundedContext.post.dto.SelectProductOption;
import com.cartmate.cartmate.boundedContext.post.dto.SelectProductResponse;

import java.util.List;

public record SalesPostSnapshot(List<ProductSnapshot> products) {

    public static SalesPostSnapshot from(List<SelectProductResponse> responses) {
        return new SalesPostSnapshot(responses.stream().map(ProductSnapshot::from).toList());
    }

    public record ProductSnapshot(
            Long id,
            Long categoryId,
            String name,
            String description,
            Long basePrice,
            List<ImageSnapshot> images,
            List<OptionSnapshot> options
    ) {
        static ProductSnapshot from(SelectProductResponse r) {
            return new ProductSnapshot(
                    r.getId(),
                    r.getCategoryId(),
                    r.getName(),
                    r.getDescription(),
                    r.getBasePrice(),
                    r.getProductImageList().stream().map(ImageSnapshot::from).toList(),
                    r.getProductOptionList().stream().map(OptionSnapshot::from).toList()
            );
        }
    }

    public record ImageSnapshot(Long imageId, String s3Key, int sortOrder) {
        static ImageSnapshot from(SelectProductImage i) {
            return new ImageSnapshot(i.getId(), i.getS3key(), i.getImageSeq());
        }
    }

    public record OptionSnapshot(Long productTypeId, Long productOptionId) {
        static OptionSnapshot from(SelectProductOption o) {
            return new OptionSnapshot(o.getProductTypeId(), o.getProductOptionId());
        }
    }
}
