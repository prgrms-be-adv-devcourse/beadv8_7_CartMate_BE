package com.cartmate.cartmate.boundedContext.productService.post.out;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesPostProductOptionRepository extends JpaRepository<SalesPostProductOption, Long> {
}
