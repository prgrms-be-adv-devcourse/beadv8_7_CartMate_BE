package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.UpdateProduct;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCountPolicy {
    private static final int MAX_PRODUCT_COUNT = 5;
    private static final int MAX_TOTAL_IMAGE_COUNT = 20;

    public boolean isSafeProductCount(List<CreateProduct> products) {
        return products != null && !products.isEmpty()
                && products.size() <= MAX_PRODUCT_COUNT;
    }

    public boolean isSafeProductImageCount(List<CreateProduct> products) {
        int totalImages = products.stream()
                .mapToInt(p -> p.getProductImageList() == null ? 0 : p.getProductImageList().size())
                .sum();

        return totalImages > 0 && totalImages <= MAX_TOTAL_IMAGE_COUNT;
    }

    public boolean isSafeUpdateProductCount(List<UpdateProduct> products) {
        return products != null && !products.isEmpty()
                && products.size() <= MAX_PRODUCT_COUNT;
    }

    public boolean isSafeUpdateProductImageCount(List<UpdateProduct> products) {
        int totalImages = products.stream()
                .mapToInt(p -> p.getProductImageList() == null ? 0 : p.getProductImageList().size())
                .sum();

        return totalImages > 0 && totalImages <= MAX_TOTAL_IMAGE_COUNT;
    }
}
