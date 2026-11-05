package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.PaybackOrderOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaybackOrderOutcomeRepository extends JpaRepository<PaybackOrderOutcome, Long> {
}
