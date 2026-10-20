package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductQueryUseCase {
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageStorage imageStorage;

    @Transactional(readOnly = true)
    public List<ProductForPurchase> findProductsForPurchase(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        List<Long> distinctIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productRepository.findAllById(distinctIds);
        if (products.isEmpty()) {
            return List.of();
        }

        Map<Long, String> imageKeyByProductId = productImageRepository.findByProductIdIn(
                        products.stream().map(Product::getId).toList()).stream()
                .filter(image -> image.getStatus() == ProductStatus.ACTIVE)
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId(),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(ProductImage::getImageSeq)),
                                image -> image.map(ProductImage::getS3Key).orElse(null)
                        )
                ));

        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return distinctIds.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .map(product -> toProductForPurchase(product, imageKeyByProductId.get(product.getId())))
                .toList();
    }

    private ProductForPurchase toProductForPurchase(Product product, String imageKey) {
        return new ProductForPurchase(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getBasePrice(),
                product.getStatus() == ProductStatus.ON_SALE,
                imageKey == null ? null : imageStorage.toUrl(imageKey)
        );
    }
}
