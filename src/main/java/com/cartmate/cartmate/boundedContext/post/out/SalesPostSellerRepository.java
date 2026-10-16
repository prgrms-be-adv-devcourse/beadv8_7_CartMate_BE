package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesPostSellerRepository extends JpaRepository<SalesPostSeller, Long> {
}
