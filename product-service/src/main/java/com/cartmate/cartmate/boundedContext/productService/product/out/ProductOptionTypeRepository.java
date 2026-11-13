package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionTypeRepository extends JpaRepository<ProductOptionType, Long> {
}
