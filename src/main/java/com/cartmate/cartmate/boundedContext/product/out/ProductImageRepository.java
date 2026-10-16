package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
}
