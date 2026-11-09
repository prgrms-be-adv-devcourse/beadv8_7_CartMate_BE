package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long>, ProductImageQueryRepository {
    List<ProductImage> findByProductIdIn(List<Long> productIds);

    List<ProductImage> findByProduct(Product product);
}
