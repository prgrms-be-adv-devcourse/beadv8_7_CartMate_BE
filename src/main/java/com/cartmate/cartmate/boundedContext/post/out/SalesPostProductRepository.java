package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesPostProductRepository extends JpaRepository<SalesPostProduct, Long> {
    List<SalesPostProduct> findByIdIn(List<Long> productIds);
}
