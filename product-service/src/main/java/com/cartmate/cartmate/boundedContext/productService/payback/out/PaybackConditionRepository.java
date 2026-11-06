package com.cartmate.cartmate.boundedContext.productService.payback.out;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackCondition;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaybackConditionRepository extends JpaRepository<PaybackCondition, Long> {

    Optional<PaybackCondition> findByProductIdAndFunder(Long productId, PaybackFunder funder);

    List<PaybackCondition> findAllByProductIdOrderByFunder(Long productId);

    List<PaybackCondition> findAllByProductIdIn(Collection<Long> productIds);
}
