package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesPostProductRepository extends JpaRepository<SalesPostProduct, Long> {
}
