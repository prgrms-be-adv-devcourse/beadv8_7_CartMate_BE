package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySalesPostId(Long salesPostId);

    void deleteBySalesPostId(Long postId);
}
