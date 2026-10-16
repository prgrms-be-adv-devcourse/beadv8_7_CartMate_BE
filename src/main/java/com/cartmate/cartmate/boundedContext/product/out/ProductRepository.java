package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
}
