package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.boundedContext.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
}
