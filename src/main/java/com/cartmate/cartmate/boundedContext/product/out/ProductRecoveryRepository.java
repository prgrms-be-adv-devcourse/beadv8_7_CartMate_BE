package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostRecovery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRecoveryRepository extends JpaRepository<SalesPostRecovery, Long> {
}
