package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.PaybackJudgment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaybackJudgmentRepository extends JpaRepository<PaybackJudgment, Long> {
}
